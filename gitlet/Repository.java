package gitlet;

import java.io.File;
import static gitlet.Utils.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/** Represents a gitlet repository.
 *  does at a high level.
 *
 *  @author Cole Tahawi
 */
public class Repository implements Serializable {
    /** The current working directory. */
    public static final File PROJ_DIR = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(PROJ_DIR, ".gitlet");
    /** The .gitlet sub directories: */
    public static final File BLOBS_DIR = join(GITLET_DIR, "blobs");
    public static final File COMMITS_DIR = join(GITLET_DIR, "commits");
    public static final File STAGED_DIR = join(GITLET_DIR, "staged");
    public static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    public static final File REPO_FILE = join(GITLET_DIR, "repository");
    private static final String MASTER_BRANCH = "master";
    /** Instance variables: */
    private String head; // String name of working branch
    /** access all branches' pointers.
     * Key = name, Value = shaVal of branch object */
    private ArrayDeque<String> branches;
    /** K = abs path of working file, V = sha val of a staged blob. */
    private HashMap<String, String> stagedFiles;
    /** list of files staged for deletion, by their abs. paths. */
    private ArrayDeque<String> doomedFiles; // (ALL CAPS)
   /** Stores remote name, remote path */
    private HashMap<String, String> remotes;
    /** Should only create a new repo object
     * ONLY IF one doesn't exist already. */
    public Repository() {
        // init data structures
        stagedFiles = new HashMap<>();
        remotes = new HashMap<>();
        doomedFiles = new ArrayDeque<>();
        branches = new ArrayDeque<>();
        // setup file structure
        setupPersistence();
        // get + save initial commit (accessed via no-arg constructor)
        Commit initCommit = new Commit();
        // get + save head branch
        Branch masterBranch = new Branch(MASTER_BRANCH, initCommit.getMySha());
        // add head branch to branch map
        branches.addLast(MASTER_BRANCH);
        // make MASTER_BRANCH working/head branch
        head = MASTER_BRANCH;
    }

    /** Called at the end of main. May recieve a null r. */
    public static void saveRepo(Repository r) {
        if (r != null) {
            writeObject(REPO_FILE, r);
        }
    }

    /** Called by main - creates new repo
     * FOR INIT COMMAND ONLY.
     * Returns null if repo has already been initialized.
     */
    public static Repository initRepository() {
        if (repoExists()) {
            // repo already exists, don't init again
            return null;
        }
        return new Repository();
    }

    private static boolean repoExists() {
        return REPO_FILE.exists();
    }

    /** Called by main - deserializes and returns repo if it exists.
     * FOR EVERY COMMAND BESIDES INIT.
     * Otherwise return null. */
    public static Repository getRepo() {
        if (repoExists()) {
            // try to get & instance repo instance
            Repository r;
            // read file
            r = readObject(REPO_FILE, Repository.class);
            return r;
        } else {
            // still need to init, return null.
            return null;
        }
    }

    /** Makes a commit in head branch, given a commit message.
     * Returns whether changes were staged to commit. */
    public boolean makeCommit(String m) {
        return makeCommit(m, null);
    }

    /** Make a commit, allows for a second parent. */
    private boolean makeCommit(String m, String secondParent) {
        // if no changes staged to commit
        if (stagedFiles.isEmpty() && doomedFiles.isEmpty()) {
            return false;
        }
        // get working branch using its sha
        Branch b = Branch.readBranch(head);
        // get new commit, also handles saving new blobs.
        Commit newC = null;
        // if only 1 parent
        if (secondParent == null) {
            newC = new Commit(m, b.getHeadCommit(), stagedFiles, doomedFiles);
        } else {
            newC = new Commit(m, b.getHeadCommit(), secondParent, stagedFiles,
                    doomedFiles);
        }
        // update branch pointer
        b.makeCommit(newC.getMySha());
        // save updated branch pointer. also updates branches w/
        // b's new sha value.
        b.saveBranch();
        // clear all staged files
        clearStaging();
        return true;
    }

    /** Wipes stagedFiles, doomedFiles, and /staged/ directory clean. */
    public void clearStaging() {
        // get staged files' names
        List<String> fNames = plainFilenamesIn(STAGED_DIR);
        // iterate over contents of staging dir
        for (String name : fNames) {
            // construct obj for this file
            File f = join(STAGED_DIR, name);
            // delete this file
            f.delete();
        }
        // empty repo's staging records
        stagedFiles = new HashMap<>();
        doomedFiles = new ArrayDeque<>();
    }

    /** Sets up structure inside .gitlet directory.
     * Returns whether folders were created. */
    private boolean setupPersistence() {
        try {
            // make directories
            GITLET_DIR.mkdir();
            COMMITS_DIR.mkdir();
            BLOBS_DIR.mkdir();
            STAGED_DIR.mkdir();
            BRANCHES_DIR.mkdir();
            // make file to serialize this object to
            REPO_FILE.createNewFile();
        } catch (IOException e) {
            System.out.println("Persistence setup failed!");
            return false;
        }
        return true;
    }

    /** Adds a file instance as a serialized blob
     * to the staged folder, if it has changed. Named by sha value.
     * Returns whether the the given file exists.
     * */
    public boolean stageFile(String path) {
        // if there is already a version of this file staged
        if (stagedFiles.containsKey(path)) {
            // get file to delete
            File f = join(STAGED_DIR, stagedFiles.get(path));
            // delete file
            f.delete();
        }
        File workingF = new File(path);
        // if desired file doesn't exist
        if (!workingF.exists()) {
            return false; // file DNE, can't stage
        }
        /** convert file instance into blob
         * (special constructor - will write to staged directory,
         * not blobs directory, but doesn't save blob automatically) */
        Blob b = new Blob(path, STAGED_DIR);
        // get working branch
        Branch br = Branch.readBranch(head);
        // get head commit
        Commit headC = Commit.readCommit(br.getHeadCommit());
        // remove file from staged to delete (if it's there)
        doomedFiles.remove(path);
        // if file hasn't changed since last commit
        if (headC.getBlobSha(path) != null
                && headC.getBlobSha(path).equals(b.getSha())) {
            // no need to stage file
            return true;
        }
        // otherwise, save new blob/staged file
        b.saveBlob(STAGED_DIR);
        // add to repo's staged files record
        stagedFiles.put(path, b.getSha());
        return true;
    }

    /** Unstage the file if it is currently staged for addition.
     * If the file is tracked in the current commit,
     * stage it for removal and remove the file from the working
     * directory if the user has not already done so
     * (do not remove it unless it is tracked in the current commit).
     * Returns whether the file could be removed in either of these ways. */
    public boolean removeFile(String path) {
        // get file obj of path
        File workingF = new File(path);
        // get head commit
        Commit headC = getHeadCommit(head);
        /** If the file is neither staged nor tracked by the head commit */
        if (!stagedFiles.containsKey(path) && headC.getBlobSha(path) == null) {
            return false;
        }
        // if file is staged
        if (stagedFiles.containsKey(path)) {
            // get file of blob in /staged/
            File stagedF = join(STAGED_DIR, stagedFiles.get(path));
            // delete blob
            stagedF.delete();
            // remove file from repo's staged record
            stagedFiles.remove(path);
        }
        // if file is tracked in head commit
        if (headC.getBlobSha(path) != null) {
            // add file to list of files to remove in commit
            doomedFiles.addLast(path);
            // if file exists in the working directory
            if (workingF.exists()) {
                restrictedDelete(workingF);
            }
        }
        return true;
    }

    /** Given a file's abs path, Takes the version of the file as it exists
     * in the head commit and puts it in the working directory, overwriting
     * the version of the file that’s already there if there is one. The new
     * version of the file is not staged.
     * Handles bad inputs (error msg and exit). */
    public void checkoutFile(String filePath) {
        // get head branch
        Branch branch = Branch.readBranch(head);
        // get last commit from head branch
        String commitSha = branch.getHeadCommit();
        // do checkout from a commit, using head commit
        checkoutFileFromCommit(filePath, commitSha);
    }

    /** Given a file's abs path & a commit's ID,
     * Takes the version of the file as it exists in the commit
     * with the given id, and puts it in the working directory,
     * overwriting the version of the file that’s already there
     * if there is one. The new version of the file is not staged.
     * Handles bad inputs (error msg and exit). */
    public void checkoutFileFromCommit(String filePath, String commitSha) {
        // deserialize commit from sha
        Commit c = Commit.readCommit(commitSha);
        // if this commit DNE
        if (c == null) {
            // show error message & quit
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        // get blob-instance for desired file
        String blobSha = c.getBlobSha(filePath);
        // if file DNE in this commit
        if (blobSha == null) {
            // show error message & quit
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
        // deserialize this blob
        Blob blob = Blob.readBlob(blobSha);
        // write this blob's contents into the working project
        blob.writeToProject();
    }

    /** Takes all files in the commit at the head of the given branch,
     * and puts them in the working directory, overwriting the versions
     * of the files that are already there if they exist. Also, at the end of
     * this command, the given branch will now be considered the current branch
     * (HEAD). Any files that are tracked in the current branch but are not
     * present in the checked-out branch are deleted. The staging area is
     * cleared, unless the checked-out branch is the current branch.
     * Handles bad inputs (error msg and exit). */
    public void checkoutBranch(String branchName) {
        /** Handle failure cases */
        // if desired branch DNE
        if (!branches.contains(branchName)) {
            // show error message & quit
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        // if desired branch is current branch
        if (head.equals(branchName)) {
            // show error message & quit
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }
        /** do checkout */
        // get head commit from given branch
        Commit headC = getHeadCommit(branchName);
        // checkout commit
        checkoutCommit(headC);
        // make given branch the head
        head = branchName;
    }

    /** Checks out all the files tracked by the given commit.
     * hardCheckout tells whether to fail b/c of untracked files in the way
     * Removes files tracked in head that are not present in given commit. */
    public void checkoutCommit(Commit c) {
        Commit headC = getHeadCommit(head);
        /** Handle failure case */
        // if there is an untracked file in the way, & should heed to it.
        if (!c.canWriteToProject(headC)) {
            // show error message & quit
            System.out.println("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
            System.exit(0);
        }
        /** Clear staging & delete files tracked in head commit */
        // files in head
        for (String path : headC.getBlobMap().keySet()) {
            // if not in the given commit, too
            if (c.getBlobSha(path) == null) {
                // delete file
                File f = new File(path);
                f.delete();
            }
        }
        // clear staging area
        clearStaging();
        /** Write this commit's files to working dir */
        c.writeToProject();
    }

    /** Checks out all the files tracked by the given commit.
     * Removes files tracked in head that are not present in given commit.
     * Makes this commit the head of the current branch.
     * Returns whether the given commit exists. */
    public boolean reset(String commitSha) {
        // if commit DNE
        if (!plainFilenamesIn(COMMITS_DIR).contains(commitSha)) {
            return false;
        }
        // deserialize commit
        Commit c = Commit.readCommit(commitSha);
        // checkout commit
        checkoutCommit(c);
        // get current branch
        Branch b = Branch.readBranch(head);
        // update this branch's current commit & save it
        b.makeCommit(c.getMySha());
        b.saveBranch();
        return true;
    }

    /** Given however many characters of a COMMIT ID,
     * Return some complete commit ID matching this prefix.
     * Return null if no matches. Simply returns a complete sha val. */
    public static String getFullSha(String prefix) {
        // if already a complete sha, return it
        int normalLength = 40;
        if (prefix.length() >= normalLength) {
            return prefix;
        }
        // iterate over all commit's sha vals
        for (String sha : plainFilenamesIn(COMMITS_DIR)) {
            // if prefix matches
            if (sha.substring(0, prefix.length()).equals(prefix)) {
                return sha;
            }
        }
        return null;
    }

    /** Given a branch's name, deserialize and return its head commit. */
    private Commit getHeadCommit(String br) {
        // deserialize branch
        Branch b = Branch.readBranch(br);
        // get head commit sha
        String cSha = b.getHeadCommit();
        // deserialize and return commit
        return Commit.readCommit(cSha);
    }

    /** Starting at the current head commit, display information about each
     *  commit backwards along the commit tree until the initial commit,
     *  following the first parent commit links, ignoring any second parents
     *  found in merge commits.
     *  */
    public void printLog() {
        printLog(getHeadCommit(head));
    }
    /** recursive structure for function */
    private void printLog(Commit c) {
        // print this commit
        c.printLog();
        // get parent of this commit's sha
        String parentSha = c.getParent();
        // if NOT at end of commit history
        if (parentSha != null) {
            // get parent commit
            Commit parent = Commit.readCommit(parentSha);
            // recurse onto parent
            printLog(parent);
        }
    }

    /** Prints every commit's log in no particular order. */
    public void printGlobalLog() {
        // iterate over contents of /commits/ (AKA every commit)
        for (String sha : plainFilenamesIn(COMMITS_DIR)) {
            // deserialize commit
            File f = join(COMMITS_DIR, sha);
            Commit c = readObject(f, Commit.class);
            // print commit's log
            c.printLog();
        }
    }

    /** Displays what branches currently exist, and marks the current branch.
     * Also displays what files have been staged for addition or removal. */
    public void printStatus() {
        printBranches();
        printFiles(stagedFiles.keySet(), "Staged Files");
        printFiles(doomedFiles, "Removed Files");
        printUnstagedMods();
        printFiles(getUntrackedFiles(PROJ_DIR, getHeadCommit(head)),
                "Untracked Files");
    }
    /** Print's repos branches, & marks the current HEAD. */
    private void printBranches() {
        System.out.println("=== Branches ===");
        // iterate over branches' names
        for (String brName : branches) {
            // if current head
            if (brName.equals(head)) {
                // mark head branch
                brName = "*" + brName;
            }
            System.out.println(brName);
        }
        System.out.println();
    }
    /** Generic file printer, takes in a set of absolute
     * file paths, and a title. Handles formatting too.  */
    private void printFiles(Iterable<String> absPaths, String message) {
        System.out.println("=== " + message + " ===");
        // iterate over staged files' abs paths
        for (String absPath : absPaths) {
            // print rel path
            String relPath = getRelPath(absPath);
            System.out.println(relPath);
        }
        System.out.println();
    }
    /** Generic file printer, takes in a set of absolute
     * file paths, and a title. Handles formatting too.  */
    private void printUnstagedMods() {
        System.out.println("=== Modifications Not Staged For Commit ===");
        // iterate over staged files' abs paths
        for (String absPath : getUnstagedMods()) {
            // print rel path
            String relPath = getRelPath(absPath);
            System.out.println(relPath);
        }
        System.out.println();
    }
    /** Returns a set of the absolute paths of
     * Files that are TRACKED IN HEAD COMMIT, &
     * have been modified since, but NOT STAGED.
     * Also marks whether files were modified or deleted. */
    private Iterable<String> getUnstagedMods() {
        ArrayDeque<String> unstagedModFiles = new ArrayDeque<>();
        Commit c = getHeadCommit(head);
        // get map of commit's paths, sha vals.
        HashMap<String, String> blobs = c.getBlobMap();
        // iterate over current commit's files
        for (String path : blobs.keySet()) {
            File f = new File(path);
            /** add f if it wasn't deleted using rm */
            // if file was deleted
            if (!f.exists()) {
                // if file wasn't deleted using rm
                if (!doomedFiles.contains(path)) {
                    // add 'deleted' marker to path
                    path += " (deleted)";
                    // add path to return list
                    unstagedModFiles.add(path);
                }
                continue; // no need to check if file was modified
            }
            /** add f if it was modified (and not staged) */
            // compute new blob, use special constructor so blob doesn't
            // serialize. (passing PROJ_DIR in particular is arbitrary)
            Blob newB = new Blob(path, PROJ_DIR);
            // if contents of this file have changed since commit/staging
            if (!newB.getSha().equals(c.getBlobSha(path))
                    && !stagedFiles.containsKey(path)) {
                // add 'deleted' marker to path
                path += " (modified)";
                // add path to return list
                unstagedModFiles.add(path);
            }
        }
        return unstagedModFiles;
    }
    /** Returns a deque of the absolute paths of
     * Files that are not tracked in the given commit, nor staged */
    private ArrayDeque<String> getUntrackedFiles(File dir, Commit c) {
        ArrayDeque<String> untrackedFiles = new ArrayDeque<>();
        // iterate over plain files in this dir
        for (String name : plainFilenamesIn(dir)) {
            // if file is hidden, ignore it
            if (name.charAt(0) == '.') {
                continue;
            }
            // get file obj
            File f = join(dir, name);
            // if file is a dir
            if (f.isDirectory()) {
                // add untracked files w/i this dir (RECURSE)
                untrackedFiles.addAll(getUntrackedFiles(f, c));
            } else {
                // get file's path
                String path = f.getAbsolutePath();
                // if not tracked in head commit, or staged
                if (c.getBlobSha(path) == null
                        && !stagedFiles.containsKey(path)) {
                    // add untracked file
                    untrackedFiles.add(path);
                }
            }
        }
        return untrackedFiles;
    }

    /** Convert an absolute path to a PROJ_DIR-relative path. */
    private String getRelPath(String absPath) {
        // get num of chars needed to offset to convert abs path to
        // PROJ_DIR-relative path.
        int pathOffset = PROJ_DIR.getAbsolutePath().length() + 1;
        // return substring
        return absPath.substring(pathOffset);
    }

    /** Prints out the ids of all commits that have the
     * given commit message, one per line.
     * Returns whether any matches were found.  */
    public boolean findPrint(String m) {
        boolean matchFound = false;
        // iterate over all commits' sha vals
        for (String sha : plainFilenamesIn(COMMITS_DIR)) {
            // deserialize commit
            Commit c = Commit.readCommit(sha);
            // if matching message found
            if (c.getMessage().equals(m)) {
                matchFound = true;
                System.out.println(sha);
            }
        }
        return matchFound;
    }

    /** Creates a new branch with the given name, and points it
     * at the current head commit. DOES NOT change the HEAD branch.
     * Returns whether the branch was created (name collision?) */
    public boolean makeBranch(String name) {
        // if name already taken
        if (branches.contains(name)) {
            return false;
        }
        // get current commit's sha
        String headSha = Branch.readBranch(head).getHeadCommit();
        // create & save branch
        Branch b = new Branch(name, headSha);
        // add branch to repo's record
        branches.add(name);
        return true;
    }

    /** Deletes the branch with the given name.
     * DOES NOT delete its commits. Handles failure cases, too. */
    public void removeBranch(String name) {
        /** Failure cases */
        // if branch DNE
        if (!branches.contains(name)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        // can't delete current branch
        if (name.equals(head)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        /** Delete branch */
        // get file of serialized branch
        File f = join(BRANCHES_DIR, name);
        // delete this file
        f.delete();
        // remove branch from repo's record
        branches.remove(name);
    }

    /** failure cases. (excluding 'untracked files in the way') */
    private void mergeFailures(String brName) {
        // if branch DNE
        if (!branches.contains(brName)) {
            // print failure and exit
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
            // if there are changes staged
        } else if (!stagedFiles.isEmpty() || !doomedFiles.isEmpty()) {
            // print failure and exit
            System.out.println("You have uncommitted changes.");
            System.exit(0);
            // if trying to merge head to into head
        } else if (head.equals(brName)) {
            // print failure and exit
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
    }

    /** handle merges */
    public void merge(String brName) {
        mergeFailures(brName);
        Branch headBr = Branch.readBranch(head);
        Branch b = Branch.readBranch(brName);
        Commit curr = getHeadCommit(head);
        Commit given = getHeadCommit(brName);
        Commit split = Branch.getSplit(curr, given);
        /** special cases */
        if (given.isAncestor(curr)) { // if b is an ancestor of headBr
            // (don't want to revert to an older commit in headBr's history)
            System.out.println("Given branch is an ancestor of "
                    + "the current branch.");
            System.exit(0);
        }
        if (curr.isAncestor(given)) { // if headBr is an ancestor of b
            checkoutBranch(brName); // checkout branch
            // move headBr pointer to b's current commit
            headBr.makeCommit(b.getHeadCommit());
            headBr.saveBranch(); // save update HEAD instance
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        } /** Figure out which files to checkout, remove, and merge */
        Set<String> paths = new HashSet<String>();
        paths.addAll(given.getBlobMap().keySet());
        paths.addAll(curr.getBlobMap().keySet());
        ArrayDeque<String> toStage = new ArrayDeque<>();
        ArrayDeque<String> toMerge = new ArrayDeque<>();
        ArrayDeque<String> toRemove = new ArrayDeque<>();
        boolean mergeConflict = false;
        for (String path : paths) {
            Blob currB = Blob.readBlob(curr.getBlobSha(path));
            Blob givenB = Blob.readBlob(given.getBlobSha(path));
            Blob splitB = Blob.readBlob(split.getBlobSha(path));
            // file only exists in given
            if (givenB != null && currB == null && splitB == null) {
                toStage.add(path);
            // only given changed this file
            } else if (blobsEqual(splitB, currB)
                    && givenB != null && !givenB.equals(splitB)) {
                toStage.add(path);
            // only changed in given, where it was deleted
            } else if (given.wasDeleted(path, split)
                    && blobsEqual(splitB, currB)) {
                toRemove.add(path);
            // file changed differently in both (may not exist in split).
            } else if (!blobsEqualNull(currB, givenB)
                    && !blobsEqualNull(currB, splitB)
                    && !blobsEqualNull(givenB, splitB)) {
                mergeConflict = true;
                toMerge.add(path);
            }
        } /** Failure case: untracked file would be overwritten/deleted. */
        for (String path : getUntrackedFiles(PROJ_DIR, curr)) {
            // if file should be removed or staged (no need to check toMerge)
            if (toRemove.contains(path) || toStage.contains(path)) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
                System.exit(0);
            }
        }
        for (String path : toStage) {
            checkoutFileFromCommit(path, given.getMySha());
            stageFile(path);
        }
        for (String path : toRemove) {
            removeFile(path);
        }
        for (String path : toMerge) {
            Blob currB = Blob.readBlob(curr.getBlobSha(path));
            Blob givenB = Blob.readBlob(given.getBlobSha(path));
            Blob newB = Blob.mergeBlobs(currB, givenB);
            newB.writeToProject();
            stageFile(path);
        } /** do merge commit */
        String m = "Merged " + brName + " into " + head + ".";
        makeCommit(m, b.getHeadCommit());
        if (mergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Returns whether these blobs are non-null and equal. */
    private static boolean blobsEqual(Blob b0, Blob b1) {
        return b0 != null && b1 != null && b0.equals(b1);
    }

    /** Returns whether two blobs are equal.
     * If they are both null, return true as well. */
    private static boolean blobsEqualNull(Blob b0, Blob b1) {
        return (b0 == null && b1 == null)
                || ((b0 != null && b1 != null) && b0.equals(b1));
    }

    /** Adds a remote repo. Returns false if this remote already exists. */
    public boolean addRemote(String name, String path) {
        if (remotes.containsKey(name)) {
            return false;
        }
        String absPath = getAbsPath(path);
        remotes.put(name, absPath);
        return true;
    }

    /** Removes a remote from this repo's list. Returns false if
     * this remote DNE. */
    public boolean removeRemote(String name) {
        if (!remotes.containsKey(name)) {
            return false;
        }
        remotes.remove(name);
        return true;
    }

    /** Given a PROJ_DIR-relative path, potentially containing '../',
     * return a string of this file's absolute path.*/
    private String getAbsPath(String p) {
        // the parent directory of /.gitlet/
        File dir = new File(PROJ_DIR.getAbsolutePath());
        for (String name : p.split("/")) {
            if (name.equals("..")) {
                dir = dir.getParentFile();
                p = p.substring(3);
            } else {
                break;
            }
        }
        File f = join(dir, p);
        return f.getAbsolutePath();
    }

    /** Push a branch to a remote repo.  */
    public void push(String remoteName, String brName) {

    }
}
