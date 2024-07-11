/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.pdf.service;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.content.PdfPageImageContent;
import android.graphics.pdf.content.PdfPageLinkContent;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.Build;
import android.os.ext.SdkExtensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.os.BuildCompat;
import androidx.core.util.Supplier;

import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY)
class PdfPageAdapter implements AutoCloseable {
    private int mPageNum;
    private int mHeight;
    private int mWidth;

    private PdfRenderer.Page mPdfRendererPage;
    private PdfRendererPreV.Page mPdfRendererPreVPage;

    PdfPageAdapter(@NonNull PdfRenderer pdfRenderer, int pageNum) {
        mPageNum = pageNum;
        mPdfRendererPage = pdfRenderer.openPage(pageNum);
        mHeight = mPdfRendererPage.getHeight();
        mWidth = mPdfRendererPage.getWidth();
    }

    PdfPageAdapter(@NonNull PdfRendererPreV pdfRendererPreV, int pageNum) {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            mPageNum = pageNum;
            mPdfRendererPreVPage = pdfRendererPreV.openPage(pageNum);
            mHeight = mPdfRendererPreVPage.getHeight();
            mWidth = mPdfRendererPreVPage.getWidth();
        }
    }

    public int getPageNum() {
        return mPageNum;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getWidth() {
        return mWidth;
    }

    public void render(@NonNull Bitmap bitmap) {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            mPdfRendererPage.render(bitmap, null, null, getRenderParams());
        } else {
            checkAndExecute(
                    () -> mPdfRendererPreVPage.render(bitmap, null, null, getRenderParams()));
        }
    }

    public void renderTile(@NonNull Bitmap bitmap,
            int left, int top, int scaledPageWidth, int scaledPageHeight) {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            int pageWidth = mPdfRendererPage.getWidth();
            int pageHeight = mPdfRendererPage.getHeight();
            Matrix transform = getTransformationMatrix(left, top, (float) scaledPageWidth,
                    (float) scaledPageHeight, pageWidth,
                    pageHeight);
            RenderParams renderParams = getRenderParams();
            mPdfRendererPage.render(bitmap, null, transform, renderParams);
        } else {
            checkAndExecute(() -> {
                {
                    int pageWidth = mPdfRendererPreVPage.getWidth();
                    int pageHeight = mPdfRendererPreVPage.getHeight();
                    Matrix transform = getTransformationMatrix(left, top, (float) scaledPageWidth,
                            (float) scaledPageHeight, pageWidth,
                            pageHeight);
                    RenderParams renderParams = getRenderParams();
                    mPdfRendererPreVPage.render(bitmap, null, transform, renderParams);
                }
            });
        }
    }

    @NonNull
    public List<PdfPageTextContent> getPageTextContents() {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.getTextContents();
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.getTextContents());
    }

    @NonNull
    public List<PdfPageImageContent> getPageImageContents() {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.getImageContents();
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.getImageContents());
    }

    @Nullable
    public PageSelection selectPageText(@NonNull SelectionBoundary start,
            @NonNull SelectionBoundary stop) {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.selectContent(start, stop);
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.selectContent(start, stop));
    }

    @NonNull
    public List<PageMatchBounds> searchPageText(@NonNull String query) {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.searchText(query);
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.searchText(query));
    }

    @NonNull
    public List<PdfPageLinkContent> getPageLinks() {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.getLinkContents();
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.getLinkContents());
    }

    @NonNull
    public List<PdfPageGotoLinkContent> getPageGotoLinks() {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            return mPdfRendererPage.getGotoLinks();
        }
        return checkAndExecute(() -> mPdfRendererPreVPage.getGotoLinks());

    }

    private Matrix getTransformationMatrix(int left, int top, float scaledPageWidth,
            float scaledPageHeight,
            int pageWidth, int pageHeight) {
        Matrix matrix = new Matrix();
        matrix.setScale(scaledPageWidth / pageWidth,
                scaledPageHeight / pageHeight);
        matrix.postTranslate(-left, -top);
        return matrix;
    }

    private RenderParams getRenderParams() {
        return checkAndExecute(() -> {
            RenderParams.Builder renderParamsBuilder = new RenderParams.Builder(
                    RenderParams.RENDER_MODE_FOR_DISPLAY);
            return renderParamsBuilder.setRenderFlags(0).build();
        });
    }

    private static void checkAndExecute(@NonNull Runnable block) {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            block.run();
        }
        throw new UnsupportedOperationException("Operation support above S");
    }

    private static <T> T checkAndExecute(@NonNull Supplier<T> block) {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            return block.get();
        }
        throw new UnsupportedOperationException("Operation support above S");
    }

    @Override
    public void close() {
        if (mPdfRendererPage != null && BuildCompat.isAtLeastV()) {
            mPdfRendererPage.close();
            mPdfRendererPage = null;
        } else {
            checkAndExecute(() -> {
                mPdfRendererPreVPage.close();
                mPdfRendererPreVPage = null;
            });
        }
    }
}
