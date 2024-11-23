package me.marti.calprovexample.jni;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.annotation.NonNull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;

/** @noinspection unused*/
public final class DavSyncRsHelpers {
    /** Gets the Uri that represents access to some directory tree.
     *  The Uri is obtained from a Document Uri that contains `"tree/{doc-id}`. */
    static Uri getDocumentTreeUri(@NonNull Uri docUri) {
        return DocumentsContract.buildTreeDocumentUri(docUri.getAuthority(), DocumentsContract.getTreeDocumentId(docUri));
    }

    static Cursor queryChildrenOfDocument(@NonNull Context context, @NonNull Uri docUri) throws NullPointerException {
        String docId = DocumentsContract.getDocumentId(docUri);
        Uri treeUri = getDocumentTreeUri(docUri);
        Cursor c = context.getContentResolver().query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
            new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
            },
            "", new String[0], ""
        );
        if (c == null) throw new NullPointerException("Cursor is NULL");
        c.moveToFirst();
        return c;
    }

    static boolean isDir(@NonNull Context context, @NonNull Uri docUri) throws NullPointerException {
        Cursor c;
        try {
            c = context.getContentResolver().query(
                docUri,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                "", new String[0], ""
            );
        } catch (Exception e) {
            return false;
        }
        if (c == null) throw new NullPointerException("Cursor is NULL");
        c.moveToFirst();

        String mime = c.getString(0);

        c.close();

        return Objects.equals(mime, DocumentsContract.Document.MIME_TYPE_DIR);
    }

    /** Appends the path to the end of the Uri's document path. */
    static @NonNull Uri joinDocUri(@NonNull Uri docUri, @NonNull String path) {
        try {
            return Uri.parse(docUri + URLEncoder.encode("/" + path, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unreachable: utf-8 is a valid encoding");
        }
    }
//    /** Returns the Uri of the Document's parent Document */
//    static @NonNull Uri docUriParent(@NonNull Uri docUri) {
//
//    }
}
