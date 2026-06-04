package com.ing.engine.cli.commands.testar;

import com.ing.engine.cli.INGeniousCLI;
import com.ing.engine.cli.commands.testar.daemon.TestarCliDaemonClient;
import com.ing.engine.cli.commands.testar.daemon.TestarCliDaemonServer;
import com.ing.engine.cli.commands.testar.daemon.TestarCliRequest;
import com.ing.engine.cli.commands.testar.TestarBddGoalLoader.ResolvedBddGoal;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.Arrays;
import java.util.concurrent.Callable;

@Command(
    name = "testar",
    mixinStandardHelpOptions = true,
    description = "TESTAR automation tooling commands",
    subcommands = {
        TestarCommand.SessionCommand.class,
        TestarCommand.NavigationCommand.class,
        TestarCommand.StateCommand.class,
        TestarCommand.ActionCommand.class,
        TestarCommand.AssertCommand.class,
        TestarCommand.DaemonCommand.class
    }
)
public class TestarCommand implements Callable<Integer> {

    @ParentCommand
    private INGeniousCLI parent;

    @Override
    public Integer call() {
        System.out.println("Use 'ingenious testar <subcommand>' - see 'ingenious testar --help'");
        System.out.println("  session    - Start, inspect, and stop a TESTAR session");
        System.out.println("  navigation - Navigate within the active TESTAR session");
        System.out.println("  state      - Query TESTAR session state");
        System.out.println("  action     - Execute TESTAR actions");
        System.out.println("  assert     - Add TESTAR assertions");
        return 0;
    }

    static final class ClientFacade {

        private final TestarCliDaemonClient client = new TestarCliDaemonClient();

        int send(INGeniousCLI cli, String command, String... arguments) {
            return TestarCliSupport.printDaemonResponse(
                    cli,
                    client.send(TestarCliRequest.of(command, Arrays.asList(arguments)))
            );
        }
    }

    @Command(
        name = "session",
        mixinStandardHelpOptions = true,
        description = "Manage a TESTAR automation session",
        subcommands = {
            SessionCommand.Start.class,
            SessionCommand.Status.class,
            SessionCommand.Stop.class
        }
    )
    public static class SessionCommand implements Callable<Integer> {

        @ParentCommand
        private TestarCommand parent;

        @Override
        public Integer call() {
            System.out.println("Use 'ingenious testar session <subcommand>'");
            return 0;
        }

        @Command(name = "start", description = "Start a TESTAR automation session")
        public static class Start implements Callable<Integer> {

            @ParentCommand
            private SessionCommand parent;

            @Option(names = {"--url"}, required = true, description = "URL to open in the TESTAR session")
            private String url;

            @Option(names = {"--bdd-scenario"}, description = "BDD scenario name for generated artifacts")
            private String bddScenarioName;

            @Option(names = {"--project", "-p"}, description = "Project path for generated TESTAR artifacts")
            private String projectPathOption;

            @Option(names = {"--bdd-goal"}, description = "BDD goal id under .agents/bdd_goals, for example parabank/requested_loans_with_small_down_payments_must_be_denied")
            private String bddGoalId;

            @Option(names = {"--bdd-file"}, description = "Path to a BDD scenario file")
            private String bddFilePath;

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                String projectPath = projectPathOption != null && !projectPathOption.trim().isEmpty()
                        ? projectPathOption
                        : cli.getProjectPath();

                if (projectPath == null || projectPath.trim().isEmpty()) {
                    cli.printError("Project path required. Use --project before 'testar' or use 'testar session start -p <projectPath> ...'.");
                    return 1;
                }

                ResolvedBddGoal resolvedGoal;
                try {
                    resolvedGoal = TestarBddGoalLoader.resolve(
                            bddGoalId,
                            bddFilePath,
                            bddScenarioName != null && !bddScenarioName.trim().isEmpty()
                                    ? bddScenarioName.trim()
                                    : "BDD CLI Scenario"
                    );
                } catch (IllegalArgumentException exception) {
                    cli.printError(exception.getMessage());
                    return 1;
                }

                return new ClientFacade().send(
                        cli,
                        "session.start",
                        projectPath,
                        resolvedGoal.getScenarioName(),
                        resolvedGoal.getBddInstructions(),
                        resolvedGoal.getSourcePath() != null ? resolvedGoal.getSourcePath().toString() : "",
                        url
                );
            }
        }

        @Command(name = "status", description = "Get the status of the current TESTAR session")
        public static class Status implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "session.status");
            }
        }

        @Command(name = "stop", description = "Stop the current TESTAR session")
        public static class Stop implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "session.stop");
            }
        }
    }

    @Command(
        name = "state",
        mixinStandardHelpOptions = true,
        description = "Query TESTAR automation state",
        subcommands = {
            StateCommand.Widgets.class,
            StateCommand.Text.class,
            StateCommand.Image.class
        }
    )
    public static class StateCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Use 'ingenious testar state <subcommand>'");
            return 0;
        }

        @Command(name = "widgets", description = "Get interactive widgets from the current TESTAR session")
        public static class Widgets implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "state.widgets");
            }
        }

        @Command(name = "text", description = "Get visible text from the current TESTAR session")
        public static class Text implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "state.text");
            }
        }

        @Command(name = "image", description = "Get a screenshot from the current TESTAR session")
        public static class Image implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "state.image");
            }
        }
    }

    @Command(
        name = "navigation",
        mixinStandardHelpOptions = true,
        description = "Navigate within the current TESTAR session",
        subcommands = {
            NavigationCommand.Url.class,
            NavigationCommand.Back.class
        }
    )
    public static class NavigationCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Use 'ingenious testar navigation <subcommand>'");
            return 0;
        }

        @Command(name = "url", description = "Get the current URL from the active TESTAR session")
        public static class Url implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "navigation.url");
            }
        }

        @Command(name = "back", description = "Navigate back in the active TESTAR session")
        public static class Back implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "navigation.back");
            }
        }
    }

    @Command(
        name = "action",
        mixinStandardHelpOptions = true,
        description = "Execute TESTAR actions",
        subcommands = {
            ActionCommand.Click.class,
            ActionCommand.Fill.class,
            ActionCommand.Select.class,
            ActionCommand.History.class
        }
    )
    public static class ActionCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Use 'ingenious testar action <subcommand>'");
            return 0;
        }

        @Command(name = "click", description = "Click an element in the current TESTAR session")
        public static class Click implements Callable<Integer> {

            @Option(names = {"--bdd-step"}, required = true, description = "BDD step associated with the click action")
            private String bddStep;

            @Option(names = {"--selector"}, required = true, description = "CSS selector of the clickable element")
            private String cssSelector;

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "action.click", bddStep, cssSelector);
            }
        }

        @Command(name = "fill", description = "Fill text into an input in the current TESTAR session")
        public static class Fill implements Callable<Integer> {

            @Option(names = {"--bdd-step"}, required = true, description = "BDD step associated with the fill action")
            private String bddStep;

            @Option(names = {"--selector"}, required = true, description = "CSS selector of the input element")
            private String cssSelector;

            @Option(names = {"--text"}, required = true, description = "Text to fill into the input element")
            private String fillText;

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "action.fill", bddStep, cssSelector, fillText);
            }
        }

        @Command(name = "select", description = "Select an option in the current TESTAR session")
        public static class Select implements Callable<Integer> {

            @Option(names = {"--bdd-step"}, required = true, description = "BDD step associated with the select action")
            private String bddStep;

            @Option(names = {"--selector"}, required = true, description = "CSS selector of the select element")
            private String cssSelector;

            @Option(names = {"--value"}, required = true, description = "Option value to select")
            private String optionValue;

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "action.select", bddStep, cssSelector, optionValue);
            }
        }

        @Command(name = "history", description = "Show executed TESTAR actions in the current session")
        public static class History implements Callable<Integer> {

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "action.history");
            }
        }
    }

    @Command(
        name = "assert",
        mixinStandardHelpOptions = true,
        description = "Add TESTAR assertions",
        subcommands = {
            AssertCommand.Add.class
        }
    )
    public static class AssertCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Use 'ingenious testar assert <subcommand>'");
            return 0;
        }

        @Command(name = "add", description = "Add an assertion text for a BDD step")
        public static class Add implements Callable<Integer> {

            @Option(names = {"--bdd-step"}, required = true, description = "BDD step associated with the assertion")
            private String bddStep;

            @Option(names = {"--text"}, required = true, description = "Visible unique text used as assertion")
            private String assertText;

            @Override
            public Integer call() {
                INGeniousCLI cli = INGeniousCLI.getInstance();
                return new ClientFacade().send(cli, "assert.add", bddStep, assertText);
            }
        }
    }

    @Command(name = "daemon", hidden = true, description = "Run the TESTAR CLI daemon")
    public static class DaemonCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            new TestarCliDaemonServer().run();
            return 0;
        }
    }
}
