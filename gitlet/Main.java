package gitlet;

import java.io.File;
import static gitlet.Utils.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Cole Tahawi
 */
public class Main {

    public static void main(String[] args) {
        // if no command given
        if (args.length == 0) {
            // print failure message & exit
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        /** get repository instance */
        Repository repo = null;
        // if repo should have been initialized previously
        if (!args[0].equals("init")) {
            repo = Repository.getRepo();
            // if repo has not been initialized
            if (repo == null) {
                // show error message & exit
                System.out.println("Not in an initialized Gitlet directory.");
                System.exit(0);
            }
        }
        /** gitlet commands */
        switch(args[0]) {
            case "init":
                repo = Repository.initRepository();
                // if already initialized repo
                if (repo == null) {
                    // show error message & quit
                    System.out.println("A Gitlet version-control system" +
                            " already exists in the current directory.");
                    System.exit(0);
                }
                break;
            case "add":
                // make given file an obj
                File f = Utils.join(Repository.PROJ_DIR, args[1]);
                // try to stage the file / if file DNE
                if (!repo.stageFile(f.getAbsolutePath())) {
                    // show error message & quit
                    System.out.println("File does not exist.");
                    System.exit(0);
                }
                break;
            case "rm":
                // get file obj
                f = Utils.join(Repository.PROJ_DIR, args[1]);
                // remove file
                boolean removed = repo.removeFile(f.getAbsolutePath());
                // if no reason to call rm
                if (!removed) {
                    // show error message & quit
                    System.out.println("No reason to remove the file.");
                    System.exit(0);
                }
                break;
            case "commit":
                // if no commit message
                if (args.length < 2 || args[1].equals("")) {
                    // show error message & quit
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                // try to make commit
                boolean changesStaged = repo.makeCommit(args[1]);
                // if no changes staged
                if (!changesStaged) {
                    // show error message & quit
                    System.out.println("No changes added to the commit.");
                    System.exit(0);
                }
                break;
            case "checkout":
                /** checkout file */
                if (args.length == 3 && args[1].equals("--")) {
                    // get file obj
                    f = join(Repository.PROJ_DIR, args[2]);
                    // do checkout
                    repo.checkoutFile(f.getAbsolutePath());
                /** checkout branch */
                } else if (args.length == 2) {
                    repo.checkoutBranch(args[1]);
                /** checkout a given file from a given commit */
                } else if (args.length == 4 && args[2].equals("--")) {
                    // get full sha val if given just a prefix
                    String commitSha = Repository.getFullSha(args[1]);
                    // get file's full path
                    String path = join(Repository.PROJ_DIR,
                            args[3]).getAbsolutePath();
                    // checkout file from specified commit (handles failures)
                    repo.checkoutFileFromCommit(path, commitSha);
                } else {
                    incorrectOps();
                }
                break;
            case "log":
                repo.printLog();
                break;
            case "global-log":
                repo.printGlobalLog();
                break;
            case "status":
                repo.printStatus();
                break;
            case "find":
                // if no matching messages found
                if (!repo.findPrint(args[1])) {
                    // show failure message
                    System.out.println("Found no commit with that message.");
                    System.exit(0);
                }
                break;
            case "branch":
                // if name taken
                if (!repo.makeBranch(args[1])) {
                    // show failure message & exit
                    System.out.println("A branch with that name already exists.");
                    System.exit(0);
                }
                break;
            case "rm-branch":
                // (handles failure cases)
                repo.removeBranch(args[1]);
                break;
            case "reset":
                String commitSha = Repository.getFullSha(args[1]);
                // try to do reset command / if commit DNE
                if (commitSha == null || !repo.reset(commitSha)) {
                    // show failure & exit
                    System.out.println("No commit with that id exists.");
                    System.exit(0);
                }
                break;
            case "add-remote":
                /** TODO: implement remote repos
                if (args.length != 3) {
                    incorrectOps();
                    System.out.println(args.length);
                }
                if (!repo.addRemote(args[1], args[2])) {
                    // show failure and exit
                    System.out.println("A remote with that name already exists.");
                    System.exit(0);
                }
                 */
                break;
            case "rm-remote":
                break;
                /** TODO: implement remote repos
                if (args.length != 2) {
                    incorrectOps();
                }
                if (!repo.removeRemote(args[1])) {
                    // show failure and exit
                    System.out.println("A remote with that name does not exist.");
                    System.exit(0);
                }
                break;
                 */
            case "merge":
                if (args.length != 2) {
                    incorrectOps();
                }
                // (handles failure cases)
                repo.merge(args[1]);
                break;
            default:
                // desired command DNE, print failure message & exit
                System.out.println("No command with that name exists.");
                System.exit(0);
                break;
        }
        // serialize repository object
        Repository.saveRepo(repo);
    }

    /** Handles failure case of wrong #/type of inputs.
     * DOES NOT determine if this failure occured. */
    private static void incorrectOps() {
        // show failure & exit
        System.out.println("Incorrect operands.");
        System.exit(0);
    }
}
