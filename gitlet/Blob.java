package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import static gitlet.Utils.*;

/** Represents the given instance of inFile. Object will be serialized,
 * named shaVal inside /blobs/
 * sha value calculated based on contents of file.
 * @author Cole Tahawi
 * */

public class Blob implements Serializable {
    private String path;
    private String shaVal;
    private String contents;

    /** Gets contents of file, computes sha, and serializes blob. */
    public Blob(String absPath) {
        // read given file
        contents = readContentsAsString(new File(absPath));
        // record absolute path of working file
        path = absPath;
        // compute this' sha
        shaVal = sha1(serialize(this));
        // save this blob
        saveBlob();
    }

    /** Gets contents of file, computes sha.
     * Creates object's file inside of /saveDir/
     * DOES NOT automatically serialize blob. */
    public Blob(String absPath, File saveDir) {
        // read given file
        contents = readContentsAsString(new File(absPath));
        // record absolute path of working file
        path = absPath;
        // compute this' sha
        shaVal = sha1(serialize(this));
    }

    /** Special/hacky constructor for merge. */
    public Blob(String absPath, String content) {
        contents = content;
        path = absPath;
        shaVal = sha1(serialize(this));
    }

    /** Returns this blob's file's absolute path. */
    public String getPath() {
        return path;
    }
    /** Returns this blob's hash value. */
    public String getSha() {
        return shaVal;
    }
    /** Returns contents of file instance this represents */
    public String getContents() {
        return contents;
    }

    /** Write this to its path in the working directory.
     * Will create file if needed. */
    public void writeToProject() {
        // get file object
        File f = new File(path);
        // make sure dirs containing f exist
        writeDirsTo(f);
        try {
            // create file if it doesn't exist
            if (!f.exists()) {
                f.createNewFile();
            }
        } catch (IOException e) {
            System.out.println("failed to write blob!");
        }
        // write contents of blob to file
        writeContents(f, contents);
    }

    /** Given a file f, and starting at PROJ_DIR,
     * ensures all directories that f demands exist do exist. */
    private void writeDirsTo(File f) {
        // represents current path, will be built-up recursively
        String buildingPath = Repository.PROJ_DIR.getAbsolutePath();
        // get PROJ_DIR-relative path
        // i.e '../proj2/test.txt' -> 'test.txt'
        String relPath = f.getPath().substring(buildingPath.length() + 1);
        // get abs path of f's directory
        String parentPath = f.getParent();
        // iterate into directories until you reach f's dir
        while (true) {
            File dir = new File(buildingPath);
            if (!dir.exists()) {
                dir.mkdir();
            }
            // split remaining path into [dirName, rest]
            String[] splitRelPath = relPath.split("/", 2);
            // if we've gotten to f's directory
            if (splitRelPath.length == 1) {
                break;
            }
            // get next/front dir's name
            String dirName = splitRelPath[0];
            // remove this name from relPath
            relPath = splitRelPath[1];
            // add next dir to buildingPath
            buildingPath += "/" + dirName;
        }
    }

    /** serializes this blob as a serialized object in the blobs directory.
     * File is named by sha value, no extension.
     * */
    public void saveBlob(File saveDir) {
        // make file object
        File f = join(saveDir, shaVal);
        // write contents to file w/ this name
        writeObject(f, this);
    }

    /** serializes a blob to /blobs/ */
    public void saveBlob() {
        saveBlob(Repository.BLOBS_DIR);
    }

    /** Returns the blob serialized in the file @ /saveDir/ */
    public static Blob readBlob(String sha, File saveDir) {
        // make file obj
        File f = join(saveDir, sha);
        // try to deserialize blob
        Blob b = null;
        b = readObject(f, Blob.class);
        return b;
    }

    /** Returns blob instance in /blobs/sha.
     * If sha val given is null, return null. */
    public static Blob readBlob(String sha) {
        if (sha == null) {
            return null;
        }
        return readBlob(sha, Repository.BLOBS_DIR);
    }

    /** Returns whether instances have the same sha val. */
    public boolean equals(Blob b) {
        return b.shaVal.equals(shaVal);
    }

    /** Given 2 blobs, merges their contents and
     * returns a new blob (not serialized) with those contents. */
    public static Blob mergeBlobs(Blob currB, Blob givenB) {
        // get contents, if file exists.
        String curr = (currB == null) ? "" : currB.getContents();
        String given = (givenB == null) ? "" : givenB.getContents();
        String newContents = "<<<<<<< HEAD\n" + curr
                + "=======\n" + given + ">>>>>>>\n";
        // use special blob constructor
        return new Blob(currB.getPath(), newContents);
    }
}
