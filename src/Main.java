import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import static java.lang.Integer.parseInt;
public class Main {

    public static final String LOG_LEVEL_MAIN = "MAIN";
    public static final String LOG_LEVEL_HANDLER = "HANDLER";
    public static final String LOG_LEVEL_EXTERNAL = "EXTERNAL";

    static HttpServer server;
    static PrintStream logger = System.out;
    static ArrayList<String> urilist;
    static boolean printTag = true;

    public static void main(String[] args) {
        HashMap<String, String> arguments = parseArgs(args);
        urilist = new ArrayList<String>();
        if(arguments.containsKey(log)) {
            try {
                logger = new PrintStream(new File(arguments.get(log)));
            } catch (Exception e) {
                log(LOG_LEVEL_MAIN, "could not open log file " + arguments.get(log) + ", using stdout.");
            }
        }
        File dir = new File(arguments.get(directory));
        File workFile = new File(arguments.get(directory) + File.separatorChar + "work");
        if(dir.exists()) {
            try {
                PrintStream ps = new PrintStream(workFile);
                for(int i = 0; i < args.length - 1; i++) {
                    ps.println(args[i]);
                }
                if(args.length > 0) ps.print(args[args.length-1]);  //no trailing new line.
                ps.close();
                logn(LOG_LEVEL_MAIN, "updating context");
                while(workFile.exists()) {
                    sleep(250);
                    logn(LOG_LEVEL_MAIN, ".");
                }
                logn(LOG_LEVEL_MAIN, "done!\n");
                log(LOG_LEVEL_MAIN, "updated context: " + arguments);
            } catch (FileNotFoundException e) {
                log(LOG_LEVEL_MAIN, e.getClass().getName());
                e.printStackTrace(logger);
            }
        } else {
            try {
                startServer(parseInt(arguments.get(port)), parseInt(arguments.get(backlog)));
                dir.mkdir();
                urilist.add(arguments.get(URI));
            } catch (IOException e) {
                log(LOG_LEVEL_MAIN, e.getClass().getName());
                e.printStackTrace(logger);
            }
            updateContext(arguments);
            while(true) {
                sleep(2000);
                if(workFile.exists()) {
                    try {
                        Scanner scan = new Scanner(workFile);
                        ArrayList<String> intermediate = new ArrayList<String>();
                        while(scan.hasNextLine()) intermediate.add(scan.nextLine());
                        scan.close();
                        args = new String[intermediate.size()];
                        for(int i = 0; i < args.length; i++) args[i] = intermediate.get(i);
                        arguments = parseArgs(args);
                        if(arguments.get(stop).equals("true")) {
                            shutdown(dir);
                            return;
                        }
                        updateContext(arguments);
                        workFile.delete();
                    } catch (FileNotFoundException e) {
                        log(LOG_LEVEL_MAIN, e.getClass().getName());
                        e.printStackTrace(logger);
                    }
                }
            }
        }
    }

    static void shutdown(File dir) {
        log(LOG_LEVEL_MAIN, "shutting down.");
        server.stop(5);
        log(LOG_LEVEL_MAIN, "server down.");
        for(File f : dir.listFiles()) f.delete();
        dir.delete();
        log(LOG_LEVEL_MAIN, "resources cleaned up.");
        log(LOG_LEVEL_MAIN, "good bye!");
        return;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void updateContext(HashMap<String, String> arguments) {
        if(urilist.contains(arguments.get(URI))) server.removeContext(arguments.get(URI));
        server.createContext(arguments.get(URI), new Page(arguments.get(bash), arguments.get(action)));
        log(LOG_LEVEL_MAIN, "updated context: " + arguments);
    }

    private static void startServer(int port, int backlog) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), backlog);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        });
        server.start();
        log(LOG_LEVEL_MAIN, "server started.");
    }

    private static final String shortArg = "-";
    private static final String longArg = "--";
    private static final String shortPort = shortArg + "p";
    private static final String port = longArg + "port";
    private static final String shortBacklog = shortArg + "B";
    private static final String backlog = longArg + "Backlog";
    private static final String shortURI = shortArg + "U";
    private static final String URI = longArg + "URI";
    private static final String shortAction = shortArg + "a";
    private static final String action = longArg + "action";
    private static final String shortLog = shortArg + "l";
    private static final String log = longArg + "log";
    private static final String shortBash = shortArg + "b";
    private static final String bash = longArg + "bash";
    private static final String shortDirectory = shortArg + "d";
    private static final String directory = longArg + "directory";
    private static final String shortStop = shortArg + "s";
    private static final String stop = longArg + "stop";

    public static HashMap<String, String> parseArgs(String[] args) {
        HashMap<String, String> answer = new HashMap<String, String>();
        for(int i = 0; i < args.length; i++) {
            if (args[i].equals(shortDirectory) || args[i].equals(directory)) {
                answer.put(directory, args[++i]);
            } else if(args[i].equals(shortStop) || args[i].equals(stop)) {
                answer.put(stop, "true");
            } else if(args[i].equals(shortBash) || args[i].equals(bash)) {
                answer.put(bash, args[++i]);
            } else if(args[i].equals(shortLog) || args[i].equals(log)) {
                answer.put(log, args[++i]);
            } else if(args[i].equals(shortAction) || args[i].equals(action)){
                answer.put(action, args[++i]);
            } else if(args[i].equals(shortURI) || args[i].equals(URI)) {
                answer.put(URI, args[++i]);
            } else if(args[i].equals(shortPort) || args[i].equals(port)) {
                answer.put(port, args[++i]);
            } else if(args[i].equals(shortBacklog) || args[i].equals(backlog)) {
                answer.put(backlog, args[++i]);
            } else {  //unrecognized command line argument.
                return null;
            }
        }
        //default settings:
        answer.putIfAbsent(port, "8080");
        answer.putIfAbsent(backlog, "0");
        answer.putIfAbsent(URI, "/");
        answer.putIfAbsent(action, "echo \"<html><body><H1>Hello World!</H1>\"; for i in `cat`; do echo \"$i<br>\"; done; echo\"</body></html>\"");
        answer.putIfAbsent(bash, "/bin/bash");
        answer.putIfAbsent(directory, System.getProperty("user.dir") + File.separatorChar + "data");
        answer.putIfAbsent(stop, "false");
        return answer;
    }

    public static void log(String logLevel, String message) {
        System.out.println(String.format("[%-10s]: ", logLevel) + message);
        printTag = true;
    }

    public static void logn(String logLevel, String message) {
        System.out.print((printTag ? String.format("[%-10s]: ", logLevel) : "") + message);
        if(!message.contains("\n")) printTag = false;
    }
}

class Page implements HttpHandler {

    String bash;
    String action;

    Page(String bash, String action) {
        this.bash = bash;
        this.action = action;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Headers qm = exchange.getRequestHeaders();
            Headers rm = exchange.getResponseHeaders();
            InputStream is = exchange.getRequestBody();
            String request = new String(is.readAllBytes());
            Main.log(Main.LOG_LEVEL_HANDLER, "recieved: " + request);
            ProcessBuilder intermediateStep = new ProcessBuilder(bash, "-c", action);
            Main.log(Main.LOG_LEVEL_HANDLER, "" + intermediateStep.command());
            Process external = intermediateStep.start();
            PrintStream toExternalInput = new PrintStream(external.getOutputStream());
            toExternalInput.println(stringify(qm));
            toExternalInput.println(stringify(rm));
            toExternalInput.println(request);
            toExternalInput.close();
            InputStream fromExternalOutput = external.getInputStream();
            InputStream fromExternalError = external.getErrorStream();
            Main.log(Main.LOG_LEVEL_HANDLER, "started action.");
            String response = new String(fromExternalOutput.readAllBytes());
            Scanner scan = new Scanner(fromExternalError);
            while (scan.hasNextLine()) {
                Main.log(Main.LOG_LEVEL_EXTERNAL, scan.nextLine());
            }
            fromExternalOutput.close();
            fromExternalError.close();
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            Main.log(Main.LOG_LEVEL_HANDLER, "sending: " + response);
            os.write(response.getBytes());
            os.close();
        } catch(Exception e) {
            Main.log(Main.LOG_LEVEL_HANDLER, "error: " + e.getClass().getName());
            e.printStackTrace(Main.logger);
            throw e;
        }
    }

    static String stringify(Headers h) {
        String answer = "";
        for(String key : h.keySet()) {
            answer += key + ":" + h.get(key);
        }
        return answer;
    }
}
