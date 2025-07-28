package server_instance;

import server_instance.codeCoverage.CodeCoverageCollector;
import server_instance.codeCoverage.CodeCoverageHelper;
import server_instance.codeCoverage.NoCodeCoverageCollector;
import util.CommandHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;

public class MernForumAppServer extends ServerInstanceManagement {

    private final int MAXIMUM_WAITING_COUNT = 20;
    private final String dockerFolder = "./src/main/java/server_instance/dockerFile/";
    private String compose_file;

    public MernForumAppServer(String appName, int server_port) {
        super(appName, server_port);
        createDockerComposeFile();
        copyVE();
        CodeCoverageCollector codeCoverageCollector = new NoCodeCoverageCollector();
        this.codeCoverageHelper = new CodeCoverageHelper(codeCoverageCollector);
    }

    private void createDockerComposeFile() {
        createDockerFileFolder();
        String compose_file_content =
                "version: '3.8'\n" +
                        "services:\n" +
                        "  mongo_db:\n" +
                        "    image: mongo:latest\n" +
                        "    restart: always\n" +
                        "    networks:\n" +
                        "      - network-db\n" +
                        "  mern-forum-app_" + (server_port % 3000) + ":\n" +
                        "    image: ntutselab/mern-forum-app:latest\n" +
                        "    ports:\n" +
                        "      - '" + server_port + ":3000'\n" +
                        "      - 8001:8000\n" +
                        "    environment:\n" +
                        "      NODE_ENV: deveplopment\n" +
                        "      PORT: 8000\n" +
                        "      BACKEND: http://localhost:8000\n" +
                        "      CLIENT: http://localhost:3000\n" +
                        "      REACT_APP_BACKEND_URL: http://localhost:8001\n" +
                        "      MONGODB: mongodb://mongo_db:27017/MERN-Forum\n" +
                        "      SECRET: ssOWR8CGLa\n" +
                        "    depends_on:\n" +
                        "      - mongo_db\n" +
                        "    networks:\n" +
                        "      - network-db\n" +
                        "networks:\n" +
                        "  network-db:\n" +
                        "    driver: bridge\n";

        compose_file = dockerFolder + "docker_compose_mernforumapp_" + (server_port % 3000) + ".yml";
        try {
            FileWriter fw = new FileWriter(compose_file);
            fw.write(compose_file_content);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            System.out.println("Error!!!");
            e.printStackTrace();
            throw new RuntimeException("Write docker file error!!");
        }
    }

    private void createDockerFileFolder() {
        File file = new File(dockerFolder);
        boolean bool = file.mkdir();
        if (bool) {
            System.out.println("Directory created successfully");
        } else {
            System.out.println("Folder is exist, not going to create it...");
        }
    }

    private void findBusyProcessAndKillIt() {
        String containerID = findBusyProcess();
        killBusyProcess(containerID);
    }

    private String findBusyProcess() {
        String containerID = CommandHelper.executeCommand("docker-compose", "-f", compose_file, "ps", "-q");
        System.out.println("find the container id is :" + containerID);
        return containerID;
    }

    private void killBusyProcess(String containerID) {
        String fixDeviceErrorScript = dockerFolder + "find-busy-mnt.sh";
        if (System.getProperty("os.name").toLowerCase().matches("(.*)windows(.*)")) {
            CommandHelper.executeCommand("powershell.exe", fixDeviceErrorScript, containerID);
        } else {
            CommandHelper.executeCommand("sh", fixDeviceErrorScript, containerID);
        }
    }

    @Override
    public void createServerInstance() {
        int waitingCount = 0;
        createServer();
        String url = "http://127.0.0.1:" + server_port + "/";
        while (!isServerActive(url, 200)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            waitingCount += 1;
            if (waitingCount == MAXIMUM_WAITING_COUNT) {
                findBusyProcessAndKillIt();
                recreateTimeOffManagement();
            } else if (waitingCount == MAXIMUM_WAITING_COUNT * 2) {
                throw new RuntimeException("Something went wrong when creating mern-forum-app...");
            }
        }
    }

    private void recreateTimeOffManagement() {
        System.out.println("recreate Server");
        closeServerInstance();
        createServer();
    }

    private void createServer() {
        long startTime = System.nanoTime();
        CommandHelper.executeCommand("docker-compose", "-f", compose_file, "up", "-d");
        long endTime = System.nanoTime();
        double timeElapsed = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("\nServer Port is " + server_port + ", Starting server instance waiting time is :" + timeElapsed);
    }

    @Override
    public void closeServerInstance() {
        String url = "http://127.0.0.1:" + server_port + "/";
        boolean isFirst = true;
        long startTime = System.nanoTime();
        while (isServerActive(url, 200)) {
            if (isFirst) {
                try {
                    Thread.sleep(1000);
                    isFirst = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException();
                }
            }
            CommandHelper.executeCommand("docker-compose", "-f", compose_file, "rm", "-svf");
        }
        long endTime = System.nanoTime();
        double timeElapsed = (endTime - startTime) / 1_000_000_000.0;
        System.out.println("\nServer Port is " + server_port + ", Closing server instance waiting time is :" + timeElapsed);
    }

    @Override
    public void restartServerInstance() {
        System.out.println("restart Server");
        closeServerInstance();
        createServerInstance();
    }

    @Override
    public String getAppName() {
        return appName;
    }

    @Override
    public void recordCoverage() {
        this.codeCoverageHelper.recordCoverage();
    }

    @Override
    public void resetCoverage() {
        codeCoverageHelper.resetCoverage();
    }

    public boolean isServerActive(String url, int expectedStatusCode) {
        int httpStatusCode = getResponseStatusCode(url);
        return httpStatusCode == expectedStatusCode;
    }

    private int getResponseStatusCode(String url) {
        int code;
        try {
            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            code = connection.getResponseCode();
        } catch (UnknownHostException e) {
            code = -1;
        } catch (SocketException e) {
            code = -2;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unknown response status code!!");
        }
        return code;
    }
}
