/*
 * Copyright 2024 maulana-id (https://github.com/maulana-id)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author maulana-id
 */

package com.maulana.MFileManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileListAdapter extends BaseAdapter {
    public interface OnChevronClickListener {
        void onChevronClick(File file);
    }

    public static final class Row {
        final File file;
        final String title;
        final String details;
        final boolean selected;
        final int depth;
        final boolean isDirectory;
        final boolean isExpanded;
        final boolean hasChildren;

        private Row(File file, String title, String details, boolean selected,
                    int depth, boolean isDirectory, boolean isExpanded, boolean hasChildren) {
            this.file = file;
            this.title = title;
            this.details = details;
            this.selected = selected;
            this.depth = depth;
            this.isDirectory = isDirectory;
            this.isExpanded = isExpanded;
            this.hasChildren = hasChildren;
        }

        public static Row file(File file, String details, boolean selected) {
            return new Row(file, null, details, selected, 0, file != null && file.isDirectory(), false, false);
        }

        public static Row treeNode(File file, String details, boolean selected,
                                   int depth, boolean isDirectory, boolean isExpanded, boolean hasChildren) {
            return new Row(file, null, details, selected, depth, isDirectory, isExpanded, hasChildren);
        }

        public static Row heading(String title) {
            return new Row(null, title, null, false, 0, false, false, false);
        }

        public boolean isHeading() {
            return file == null;
        }
    }

    private static final int THUMBNAIL_EDGE = 72;
    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> pendingThumbnails = Collections.synchronizedSet(new HashSet<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private OnChevronClickListener chevronClickListener;
    private boolean animationsEnabled = true;
    private int lastAnimatedPosition = -1;
    private final LruCache<String, Bitmap> thumbnailCache = new LruCache<String, Bitmap>(12 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    public FileListAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setOnChevronClickListener(OnChevronClickListener listener) {
        this.chevronClickListener = listener;
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
        notifyDataSetChanged();
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeading() ? 0 : 1;
    }

    @Override
    public boolean isEnabled(int position) {
        return !getItem(position).isHeading();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = getItem(position);
        if (row.isHeading()) {
            TextView heading = convertView instanceof TextView
                    ? (TextView) convertView : new TextView(parent.getContext());
            heading.setText(row.title);
            heading.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Small);
            int horizontal = dp(parent.getContext(), 8);
            int vertical = dp(parent.getContext(), 4);
            heading.setPadding(horizontal, vertical, horizontal, vertical);
            return heading;
        }

        View view = convertView;
        RowHolder holder;
        if (view == null || view instanceof TextView) {
            view = inflater.inflate(R.layout.file_list_item, parent, false);
            holder = new RowHolder(view);
            view.setTag(holder);
        } else {
            holder = (RowHolder) view.getTag();
            if (holder == null) {
                holder = new RowHolder(view);
                view.setTag(holder);
            }
        }

        // Tree Indentation spacer
        int indentWidth = row.depth * dp(parent.getContext(), 14);
        ViewGroup.LayoutParams spacerParams = holder.indentSpacer.getLayoutParams();
        if (spacerParams.width != indentWidth) {
            spacerParams.width = indentWidth;
            holder.indentSpacer.setLayoutParams(spacerParams);
        }

        // Tree expand/collapse chevron
        final ImageView chevronView = holder.treeChevron;
        if (row.isDirectory) {
            chevronView.setVisibility(View.VISIBLE);
            chevronView.setImageResource(row.isExpanded
                    ? R.drawable.ic_tree_collapse
                    : R.drawable.ic_tree_expand);
            chevronView.setRotation(0f);
            chevronView.setOnClickListener(v -> {
                if (animationsEnabled) {
                    chevronView.animate()
                            .rotationBy(row.isExpanded ? -90f : 90f)
                            .setDuration(160)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                if (chevronClickListener != null) {
                    chevronClickListener.onChevronClick(row.file);
                }
            });
        } else {
            // Invisible keeps column alignment so file names line up with folders
            chevronView.setVisibility(View.INVISIBLE);
            chevronView.setOnClickListener(null);
        }

        if (animationsEnabled && position > lastAnimatedPosition && position < 30) {
            lastAnimatedPosition = position;
            view.setAlpha(0.25f);
            view.setTranslationY(dp(parent.getContext(), 5));
            view.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            view.setAlpha(1.0f);
            view.setTranslationY(0f);
        }

        holder.name.setText(row.file.getName());
        holder.details.setText(row.details);
        holder.selectionMark.setVisibility(row.selected ? View.VISIBLE : View.GONE);
        int iconResource = iconFor(row.file);
        holder.icon.setImageResource(iconResource);

        String thumbnailKey = thumbnailKey(row.file);
        if (thumbnailKey == null) {
            holder.icon.setTag(null);
        } else {
            Bitmap cached = thumbnailCache.get(thumbnailKey);
            if (cached != null) {
                holder.icon.setImageBitmap(cached);
                holder.icon.setTag(thumbnailKey);
            } else {
                holder.icon.setTag(thumbnailKey);
                requestThumbnail(row.file, thumbnailKey);
            }
        }
        return view;
    }

    public void close() {
        closed = true;
        thumbnailExecutor.shutdownNow();
        thumbnailCache.evictAll();
    }

    private void requestThumbnail(File file, String key) {
        if (!pendingThumbnails.add(key)) {
            return;
        }
        thumbnailExecutor.execute(() -> {
            Bitmap bitmap = createThumbnail(file);
            if (bitmap != null) {
                thumbnailCache.put(key, bitmap);
            }
            pendingThumbnails.remove(key);
            if (bitmap != null && !closed) {
                mainHandler.post(() -> {
                    if (!closed) {
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private Bitmap createThumbnail(File file) {
        String extension = extension(file);
        try {
            if (isImage(extension)) {
                return imageThumbnail(file);
            }
            if (isVideo(extension)) {
                return videoThumbnail(file);
            }
            if ("pdf".equals(extension)) {
                return pdfThumbnail(file);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private Bitmap imageThumbnail(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > THUMBNAIL_EDGE * 2
                || bounds.outHeight / options.inSampleSize > THUMBNAIL_EDGE * 2) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return scaleToEdge(decoded);
    }

    private Bitmap videoThumbnail(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return scaleToEdge(retriever.getFrameAtTime(-1));
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
    }

    private Bitmap pdfThumbnail(File file) throws IOException {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            if (renderer.getPageCount() == 0) {
                return null;
            }
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                float scale = Math.min((float) THUMBNAIL_EDGE / page.getWidth(),
                        (float) THUMBNAIL_EDGE / page.getHeight());
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        }
    }

    private Bitmap scaleToEdge(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int largestSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largestSide <= THUMBNAIL_EDGE) {
            return bitmap;
        }
        float scale = (float) THUMBNAIL_EDGE / largestSide;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private int iconFor(File file) {
        if (file.isDirectory()) {
            return R.drawable.ic_file_folder;
        }
        String extension = extension(file);
        if (isImage(extension)) {
            return R.drawable.ic_file_image;
        }
        if (isVideo(extension)) {
            return R.drawable.ic_file_video;
        }
        if (isAudio(extension)) {
            return R.drawable.ic_file_audio;
        }
        if (isArchive(extension)) {
            return R.drawable.ic_file_archive;
        }
        if ("apk".equals(extension)) {
            return R.drawable.ic_file_apk;
        }
        if ("pdf".equals(extension)) {
            return R.drawable.ic_file_pdf;
        }
        if (isCode(extension)) {
            return R.drawable.ic_file_code;
        }
        if (isDocument(extension)) {
            return R.drawable.ic_file_document;
        }
        return R.drawable.ic_file_generic;
    }

    private String thumbnailKey(File file) {
        String extension = extension(file);
        if (!isImage(extension) && !isVideo(extension) && !"pdf".equals(extension)) {
            return null;
        }
        return file.getAbsolutePath() + ':' + file.lastModified() + ':' + file.length();
    }

    private String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImage(String extension) {
        return hasExtension(extension, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif");
    }

    private boolean isVideo(String extension) {
        return hasExtension(extension, "mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi", "mpeg", "mpg");
    }

    private boolean isAudio(String extension) {
        return hasExtension(extension, "mp3", "m4a", "aac", "wav", "ogg", "flac", "mid", "midi", "opus");
    }

    private boolean isArchive(String extension) {
        return hasExtension(extension, "zip", "rar", "7z", "tar", "gz", "bz2", "xz");
    }

    private boolean isCode(String extension) {
        return hasExtension(extension, "java", "kt", "py", "js", "ts", "json", "xml", "html", "css", "gradle", "sh", "c", "cpp");
    }

    private boolean isDocument(String extension) {
        return hasExtension(extension, "txt", "rtf", "csv", "doc", "docx", "odt", "xls", "xlsx", "ppt", "pptx");
    }

    private boolean hasExtension(String extension, String... extensions) {
        for (String candidate : extensions) {
            if (candidate.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class RowHolder {
        final View indentSpacer;
        final ImageView treeChevron;
        final ImageView icon;
        final ImageView selectionMark;
        final TextView name;
        final TextView details;

        RowHolder(View view) {
            indentSpacer = view.findViewById(R.id.indentSpacer);
            treeChevron = view.findViewById(R.id.treeChevron);
            icon = view.findViewById(R.id.fileIcon);
            selectionMark = view.findViewById(R.id.selectionMark);
            name = view.findViewById(R.id.fileName);
            details = view.findViewById(R.id.fileDetails);
        }
    }
}
