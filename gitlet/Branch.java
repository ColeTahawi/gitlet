package gitlet;

import java.io.File;
import java.io.Serializable;
import static gitlet.Utils.*;

/** Represents a branch pointer in gitlet. Holds references to its name,
 * sha value, and the most recent commit.
 * @author Cole Tahawi */
public class Branch implements Serializable {
    private String name; // my name
    private String head; // sha val of current commit

    /** Default constructor. Serializes this new branch, too. */
    public Branch(String n, String firstCommitSha) {
        name = n;
        head = firstCommitSha;
        saveBranch();
    }

    /** Returns the most recent commit's sha value */
    public String getHeadCommit() {
        return head;
    }

    /** Add a new commit to this branch.
     * ONLY overwrites instance variable.
     * Commit class handles commits tree.
     */
    public void makeCommit(String sha) {
        head = sha;
    }

    /** Serializes branch and writes it to BRANCHES dir.
     * Serialized branch is named its user name, NOT SHA.
     * If a branch of the same name is already serialized,
     * it is overwritten. */
    public void saveBranch() {
        // make file object
        File f = join(Repository.BRANCHES_DIR, name);
        // write contents to file w/ this name
        writeObject(f, this);
    }

    /** Deserializes + returns a branch instance, given its name.
     * Returns null if this fails/serialized branch D.N.E.
     */
    public static Branch readBranch(String name) {
        // make file obj
        File f = join(Repository.BRANCHES_DIR, name);
        // make sure branch exists
        if (!f.exists()) {
            System.out.println("Cannot deserialize this branch, "
                    + "it does not exist!");
            return null;
        }
        Branch b = null;
        // try to read in file
        b = readObject(f, Branch.class);
        return b;
    }

    /** Given a working file's absolute path,
     * deserializes & returns the blob instance of it,
     * from the most recent commit. */
    public Blob getLastBlob(String path) {
        return getBlob(path, head);
    }

    /** Given a working file's absolute path,
     * And a commit's sha,
     * deserializes & returns the blob instance of it,
     * from that commit. */
    public Blob getBlob(String path, String commitSha) {
        // deserialize commit
        Commit c = Commit.readCommit(commitSha);
        // get desired blob's sha
        String blobSha  = c.getBlobSha(path);
        // deserialize + return blob
        return Blob.readBlob(blobSha);
    }

    /** Find nearest split of the given commits using a BFS. */
    public static Commit getSplit(Commit c0, Commit c1) {
        // if at initial commit
        if (c0.getParent() == null) {
            return c0;
        }
        // get c0's parent(s)
        Commit parent = Commit.readCommit(c0.getParent());
        String parent2Sha = c0.getSecondParent();
        Commit parent2 = (parent2Sha == null) ? null : Commit.readCommit(parent2Sha);
        // if a parent is an ancestor of c1
        if (parent.isAncestor(c1)) {
            return parent; // found closest ancestor!
        } else if (parent2 != null && parent2.isAncestor(c1)) {
            return parent2; // found closest ancestor!
        }
        // otherwise, recurse onto c0's parents
        if (parent2 == null) {
            // no choice to be made
            return getSplit(parent, c1);
        }
        // return whichever split point is more proximate
        Commit firstSplit = getSplit(parent, c1);
        Commit secondSplit = getSplit(parent2, c1);
        return getCloserCommit(c1, firstSplit, secondSplit);
    }

    /** Returns whether c0 or c1 is more recent in ref's commit history. */
    private static Commit getCloserCommit(Commit ref, Commit c0, Commit c1) {
        if (ref.distanceTo(c0) < ref.distanceTo(c1)) {
            return c0;
        } else {
            return c1;
        }
    }

    /** Save this branch, and all blobs and commits
     * not in a given remote, to that remote. (given the path for /.gitlet/ */
    public void saveRemote(String remotePath) {
        File remote = new File(remotePath);
        File branch = join(remote, "branches/" + name);

    }
}
