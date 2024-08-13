package me.marti.calprovexample.jni;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** @noinspection unused*/
public final class DavSyncRsHelpers {
    static boolean checkUniqueName(@NonNull Context context, @NonNull String name) throws Exception {
        Boolean result = DavSyncRsHelpersKt.INSTANCE.checkUniqueName(context.getContentResolver(), name);
        if (result == null) {
            throw new Exception("Failed to query Content Provider (was NULL)");
        } else {
            return result;
        }
    }

    /** Gets the Uri that represents access to some directory tree.
     *  The Uri is obtained from a Document Uri that contains `"tree/{doc-id}`. */
    static Uri getDocumentTreeUri(@NonNull Uri docUri) {
        return DocumentsContract.buildTreeDocumentUri(docUri.getAuthority(), DocumentsContract.getTreeDocumentId(docUri));
    }

    static @Nullable Cursor queryChildrenOfDocument(@NonNull Context context, @NonNull Uri uri) {
        String docId = DocumentsContract.getDocumentId(uri);
        Uri treeUri = DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), docId);
        Cursor c = context.getContentResolver().query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
            new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
            },
            "", new String[0], ""
        );
        if (c != null) c.moveToFirst();
        return c;
    }
}

