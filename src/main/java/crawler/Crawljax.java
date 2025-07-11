package crawler;

import com.crawljax.core.CrawljaxRunner;
import com.crawljax.core.state.StateFlowGraph;
import directive_tree.CrawlerDirective;
import ntut.edu.tw.irobot.CrawlJaxRunnerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import usecase.learningPool.learningTask.LearningTask;
import ntut.edu.aiguide.crawljax.plugins.AIGuidePlugin;
import ntut.edu.aiguide.crawljax.plugins.domain.Action;
import ntut.edu.aiguide.crawljax.plugins.domain.LearningTarget;
import ntut.edu.aiguide.crawljax.plugins.domain.State;
import server_instance.ServerInstanceManagement;
import util.Config;
import util.HighLevelAction;
import util.LogHelper;

import java.util.*;

public class Crawljax implements Crawler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Crawljax.class);
    private StateFlowGraph stateFlowGraph;
    private final ServerInstanceAdapter serverInstanceManagement;
    private Map<String, String> domHashCompareTable;

    public Crawljax(ServerInstanceManagement serverInstance) {
        this.serverInstanceManagement = new ServerInstanceAdapter(serverInstance);
        this.domHashCompareTable = new HashMap<>();
    }

    @Override
    public List<LearningTask> crawlingWithDirectives(Config config, List<CrawlerDirective> crawlerDirectives) {
        System.out.println("Directive size: " + crawlerDirectives.size());
        for (CrawlerDirective crawlerDirective: crawlerDirectives){
            System.out.println("===========Directive===========");
            for (HighLevelAction highLevelAction: crawlerDirective.getHighLevelActions()){

                System.out.println("\t===========HighLevelAction===========");
                for (util.Action action: highLevelAction.getActionSequence()){
                    System.out.println("\t"+ action.getXpath() + ", " + action.getValue());
                }
            }
        }
        serverInstanceManagement.recordCoverage();
        LogHelper.debug("Start crawling");
        AIGuidePlugin aiGuidePlugin = createAIGuidePlugin(crawlerDirectives, config.AUT_PORT);
        CrawljaxRunner crawljaxRunner = createCrawljaxRunner(config, aiGuidePlugin);
        crawljaxRunner.call();
        List<LearningTarget> learningTargets = aiGuidePlugin.getLearningTarget();
        serverInstanceManagement.recordCoverage();
//        mergingGraph(aiGuidePlugin.getStateFlowGraph());
        return convertToLearningTask(learningTargets);
    }

    private List<LearningTask> convertToLearningTask(List<LearningTarget> learningTargets) {
        List<LearningTask> learningTasks = new LinkedList<>();
        LOGGER.debug("function convertToLearningTask()");

        serverInstanceManagement.recordCoverage();
        for (LearningTarget learningTarget : learningTargets) {
            String domHash = learningTarget.getDom().hashCode() + "";
            LOGGER.debug("The domHash is {}", domHash);
            LOGGER.debug("The targetURL is {}", learningTarget.getTargetURL());
            String stateID = (learningTarget.getTargetURL()).hashCode() + "";

            LOGGER.debug("The formXPaths is {}", learningTarget.getFormXPaths());
            LOGGER.debug("The stateID is {}", stateID);
            domHashCompareTable.put(stateID, domHash);
            learningTasks.add(
                    new LearningTask(
                            convertToUtilAction(learningTarget.getActionSequence()),
                            serverInstanceManagement.getTotalStatementCoverage(),
                            learningTarget.getTargetURL(),
                            stateID,
                            learningTarget.getDom(),
                            learningTarget.getFormXPaths(),
                            new HashMap<>()
                    )
            );
        }
        LogHelper.debug("End crawling");
        return learningTasks;
    }

    private List<List<util.Action>> convertToUtilAction(List<List<Action>> actions) {
        List<List<util.Action>> convertAction = new LinkedList<>();
        for (List<Action> actionSequence : actions) {
            List<util.Action> convertActionSequence = new ArrayList<>(actionSequence.size());
            for (Action action : actionSequence)
                convertActionSequence.add(new util.Action(action.getActionXpath(), action.getValue()));
            convertAction.add(convertActionSequence);
        }
        return convertAction;
    }

    /**
     *
     * @param crawlerDirectives
     *          the order is leaf to root
     * @param AUT_PORT
     * @return
     */
    private AIGuidePlugin createAIGuidePlugin(List<CrawlerDirective> crawlerDirectives, int AUT_PORT) {
        Stack<State> directiveStack = new Stack<>();
        for (CrawlerDirective crawlerDirective : crawlerDirectives)
            directiveStack.push(createCrawlerState(domHashCompareTable.get(crawlerDirective.getStateId()), crawlerDirective.getDom(), crawlerDirective.getHighLevelActions()));
        return new AIGuidePlugin(directiveStack, serverInstanceManagement, AUT_PORT);
    }


    private State createCrawlerState(String domHash, String dom, List<HighLevelAction> highLevelActions) {
        LinkedList<List<Action>> actions = new LinkedList<>();
        for (HighLevelAction action : highLevelActions)
            actions.add(transferToCrawlerAction(action.getActionSequence()));
        return new State(domHash, dom, actions);
    }

    private List<Action> transferToCrawlerAction(List<util.Action> actionSequence) {
        List<Action> transferAction = new LinkedList<>();
        for(util.Action action : actionSequence)
            transferAction.add(new Action(action.getXpath(), action.getValue()));
        return transferAction;
    }

    private CrawljaxRunner createCrawljaxRunner(Config config, AIGuidePlugin aiGuidePlugin) {
//        MyCrawlJaxRunnerFactory crawljaxFactory = new MyCrawlJaxRunnerFactory();
        CrawlJaxRunnerFactory crawljaxFactory = new CrawlJaxRunnerFactory();

        crawljaxFactory.setDepth(config.CRAWLER_DEPTH);
        crawljaxFactory.setHeadLess(false);
        crawljaxFactory.setRecordMode(true);
        crawljaxFactory.setClickOnce(true);
        crawljaxFactory.setWrapElementMode(true);
        crawljaxFactory.setEventWaitingTime(2000);
        crawljaxFactory.setPageWaitingTime(2000);
        return crawljaxFactory.createCrawlerCrawlJaxRunner(config.ROOT_URL, aiGuidePlugin);
    }

    @Override
    public void generateGraph() {

    }

}
