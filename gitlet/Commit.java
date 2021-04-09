package gitlet;

import java.io.Serializable;
import java.util.Date;
import java.io.File;
import java.util.HashMap;
import java.util.ArrayDeque;
import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *
 *  does at a high level.
 *
 *  @author Cole Tahawi
 */
@SuppressWarnings("deprecation")
public class Commit implements Serializable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    private String shaVal; // this' sha value
    private String message; // The message of this Commit.
    private String timestamp;
    private String prevCommit; // its sha value
    private String prevCommit2; // only for merge commits
    private HashMap<String, String> blobMap; // K=working file path, V=blob's sha val
    private ArrayDeque<String> deletedFiles;

    /** Constructor for INITIAL COMMIT ONLY. */
    public Commit() {
        message = "initial commit";
        timestamp = formatTimestamp(new Date(70, 1, 1, 0, 0, 0));
        prevCommit = null;
        blobMap = new HashMap<>();
        deletedFiles = new ArrayDeque<>();
        shaVal = computeSha();
        saveCommit();
    }

    /** Constructor if there are previous commits. */
    public Commit(String m, String prevC, HashMap<String, String> stagedBlobs,
                  ArrayDeque<String> doomedFiles) {
        message = m;
        timestamp = formatTimestamp(new Date());
        prevCommit = prevC;
        blobMap = makeBlobMap(stagedBlobs, doomedFiles);
        deletedFiles = doomedFiles;
        shaVal = computeSha();
        saveCommit();
    }

    /** Constructor if there are previous commits, and a second parent.
     * For merge commits. */
    public Commit(String m, String prevC, String secondParent, HashMap<String, String> stagedBlobs,
                  ArrayDeque<String> doomedFiles) {
        message = m;
        timestamp = formatTimestamp(new Date());
        prevCommit = prevC;
        prevCommit2 = secondParent;
        blobMap = makeBlobMap(stagedBlobs, doomedFiles);
        deletedFiles = doomedFiles;
        shaVal = computeSha();
        saveCommit();
    }

    /** formats a timestamp for logs */
    private String formatTimestamp(Date d) {
        return String.format("%1$ta %1$tb %1$td %1$tH:%1$tM:%1$tS %1$tY %1$tz",
                d);
    }

    /** Returns a record of this commit's blobs,
     * As a map (K=abs path, V=sha val). */
    public HashMap<String, String> getBlobMap() {
        return blobMap;
    }

    /** Return this' commmit message. */
    public String getMessage() {
        return message;
    }

    /** Given the path to this blob's file,
     * Returns this blob. */
    public String getBlobSha(String path) {
        return blobMap.get(path);
    }

    /** This' sha val */
    public String getMySha() {
        return shaVal;
    }

    /** Compute & return this' sha val (using byte-cast of this) */
    private String computeSha() {
        return sha1(serialize(this));
    }

    /** Returns parent commit's sha value */
    public String getParent() {
        return prevCommit;
    }

    /** Returns the second parent's commit's sha value.
     * If no second parent, return null. */
    public String getSecondParent() {
        return prevCommit2;
    }

    /** Given staged blobs, figures out what this commit's blobs should be.
     * New blobs are also saved in the blobs folder.
     * File objects correspond to a file in the working directory. */
    private HashMap<String, String> makeBlobMap(
            HashMap<String, String> stagedBlobs,
            ArrayDeque<String> doomedFiles) {
        // get previous commit from its sha
        Commit prevC = readCommit(prevCommit);
        // make new blob map, is a shallow copy of prev commit's map.
        HashMap<String, String> newMap = new HashMap<>(prevC.blobMap);
        // iterate over staged blobs' absolute paths
        for (String path : stagedBlobs.keySet()) {
            // get this blob's sha
            String newSha = stagedBlobs.get(path);
            // add this blob to commit's map
            newMap.put(path, newSha);
            // get old commit's sha value for this file
            String oldSha = prevC.getBlobSha(path);
            // if this file changed (or didn't exist before)
            if (oldSha == null || !oldSha.equals(newSha)) {
                /** to write new blob into blobs dir,
                 * deserialize from /staged/ and write to /blobs/. */
                // read blob from staged directory
                Blob newBlob = Blob.readBlob(newSha, Repository.STAGED_DIR);
                // write blob into /blobs/
                newBlob.saveBlob();
            }
        }
        // iterate over files staged for deletion
        for (String path : doomedFiles) {
            // remove this file from the new map
            newMap.remove(path);
        }
        return newMap;
    }

    /** Serialize this commit.
     * Saves to commits dir with sha value as name. */
    private void saveCommit() {
        // make file object
        File f = join(Repository.COMMITS_DIR, shaVal);
        // write contents to file w/ this name
        writeObject(f, this);
    }

    /** save commit to a /commits/ in a given /.gitlet/ */
    private void saveRemote(String remotePath) {
        File f = join(remotePath, "blobs/" + shaVal);
        writeObject(f, this);
    }

    /** Deserialize and returns a commit object,
     *  given the sha value of the commit.
     *  If commit D.N.E., or read fails, return null. */
    public static Commit readCommit(String sha) {
        // make file object
        File f = join(Repository.COMMITS_DIR, sha);
        if (!f.exists()) {
            return null;
        }
        Commit c;
        // read in object
        c = readObject(f, Commit.class);
        return c;
    }

    /** Write all blobs from this commit into working project,
     * creating files as needed. */
    public void writeToProject() {
//        System.err.println(blobMap);
        // iterate over blob map
        for (String path : blobMap.keySet()) {
            // read blob
//            System.err.println("Path: " + path + "\nHash: " + blobMap.get(path));
            Blob b = Blob.readBlob(blobMap.get(path));
            // write to project
            b.writeToProject();
        }
    }

    /** Print out the log message for this commit. */
    public void printLog() {
        System.out.println("===");
        System.out.println("commit " + getMySha());
        // if this is a merge commit
        if (prevCommit2 != null) {
            // print prefixes of both parents
            int numChars = 7;
            String prefix1 = prevCommit.substring(0, numChars);
            String prefix2 = prevCommit2.substring(0, numChars);
            System.out.println("Merge: " + prefix1 + " " + prefix2);
        }
        System.out.println("Date: " + timestamp);
        System.out.println(message + "\n");
    }

    /** Returns if there is an untracked file (relative to head)
     * that would be overwritten by writing this commit to the working project.
     * */
    public boolean canWriteToProject(Commit head) {
        return canWriteToProject(head, Repository.PROJ_DIR);
    }
    /** Recurses into directories contained in this one, excluding hidden dirs.
     * helper for canWriteToProject. */
    private boolean canWriteToProject(Commit head, File dir) {
        // iterate over plain files in
        for (String name : plainFilenamesIn(dir)) {
            // if file is hidden, ignore it
            if (name.charAt(0) == '.') {
                continue;
            }
            // get file obj
            File f = join(dir, name);
            // if this file is a dir, and contains file(s) in the way (RECURSE)
            if (f.isDirectory() && !canWriteToProject(head, f)) {
                return false;
            }
            // get file's path
            String path = f.getAbsolutePath();
            // if not tracked in head commit, & would be overwritten by this
            if (head.getBlobSha(path) == null && getBlobSha(path) != null) {
                return false;
            }
        }
        return true;
    }

    /** Returns whether this is an ancestor of c. */
    public boolean isAncestor(Commit c) {
        // if match found
        if (c.getMySha().equals(shaVal)) {
            return true;
        }
        // else, try this commit's parent
        String parentSha = c.getParent();
        // if at end of commit history
        if (parentSha == null) {
            return false;
        }
        // otherwise, recurse onto parent
        Commit parent = Commit.readCommit(parentSha);
        return isAncestor(parent);
    }

    /** Returns whether the file @ path was deleted
     * AT SOME POINT in this' history, more recently than the given c. */
    public boolean wasDeleted(String path, Commit c) {
        Commit currC = this;
        // iterate thru this' commit history, until reaching c.
        while (!currC.getMySha().equals(c.getMySha())) {
            if (currC.deletedFiles.contains(path)) {
                return true;
            }
            currC = readCommit(currC.getParent());
        }
        return false;
    }

    /** Returns how many commits back into this' history c is.
     * (i.e. If c is this commit, returns 0.) */
    public int distanceTo(Commit c) {
        Commit currC = this;
        int distance = 0;
        while (!currC.getMySha().equals(c.getMySha())) {
            distance += 1;
            currC = readCommit(currC.getParent());
        }
        return distance;
    }
}
