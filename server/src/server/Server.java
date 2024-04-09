package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.*;

public class Server {
    private static final int PORT = 8845;
    private static final String CONFIG_FILE = "config.txt";
    private static String ROOT_DIRECTORY;
    private static final String LOG_FILE = "server.log";

    private Map<String, String> userCredentials;
 
    private Logger logger;

    public Server() {
        loadConfig();
        initializeLogger();
    }

    private void loadConfig() {
        // 读取配置文件，加载用户凭据
        userCredentials = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            int i=0;
            while ((line = reader.readLine()) != null) {
            	if(i<10) {//只存了10个用户
                String[] tokens = line.split(":");
                if (tokens.length == 2) {
                    String username = tokens[0].trim();
                    String password = tokens[1].trim();
                    userCredentials.put(username, password);
                    i++;}
                }else {ROOT_DIRECTORY=System.getProperty("user.dir")+line;}
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    private void initializeLogger() {
        try {
            // 创建日志记录器
            logger = Logger.getLogger(Server.class.getName());

            // 创建日志文件处理器
            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(new SimpleFormatter());

            // 设置日志级别和处理器
            logger.setLevel(Level.INFO);
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server started and listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandler.start();//启动一个新的线程来处理客户端连接
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String rootDirectory= ROOT_DIRECTORY;
        int cdFlag = 0;
        public ClientHandler(Socket socket) {
            clientSocket = socket;
        }

        @Override
        public void run() {
            try {
            	
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                while(true) {//密码错误就再输入
	                if (authenticateUser()) {
	                    out.println("Authentication successful. Welcome, " + username + "!");
	                    logger.info("Login successful: User - " + username + ", IP - " + clientSocket.getInetAddress());

	                    String command;
	
	                    while ((command = in.readLine()) != null) {
	                        if (command.equalsIgnoreCase("exit")) {
	                            logger.info(" User - " + username + ", IP - " + clientSocket.getInetAddress()+"    "+command);
	                            break;
	                        }
	
	                        processCommand(command);
	                    }
	                    break;
	                }else{
	                	 logger.warning("Login failed: IP - " + clientSocket.getInetAddress());
	                };
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean authenticateUser() throws IOException {

            username = in.readLine().trim();

            if (username.equalsIgnoreCase("anonymous")) {
                return true;
            }

            String password = in.readLine().trim();
            if(!userCredentials.containsKey(username)) {
            	out.println("wrong username");
                logger.warning("wrong username");

            }else if(!userCredentials.get(username).equals(password)){
            	out.println("wrong password");
            	 logger.warning("wrong password");
            }

            return userCredentials.containsKey(username) && userCredentials.get(username).equals(password);
        }

        private void processCommand(String command) {
            String[] tokens = command.split(" ");
            String action = tokens[0].toLowerCase();

            switch (action) {
                case "dir":
                    listFiles();
                    break;
                case "cd":
                    if (tokens.length > 1) {
                        String directory = tokens[1];
                        changeDirectory(directory);
                    }
                    break;
                case "put":
                    if (tokens.length > 1) {
                        String filename = tokens[1];
                        uploadFile(filename);
                    }
                    break;
                case "get":
                    if (tokens.length > 1) {
                        String filename = tokens[1];
                        downloadFile(filename);
                    }
                case "exit":
                    break;
                default:
                    out.println("Invalid command");
                    
            }
            logger.info(" User - " + username + ", IP - " + clientSocket.getInetAddress()+"    "+command);

        }

        private void listFiles() {
            File directory = new File(rootDirectory);
            File[] files = directory.listFiles();
           

            if (files != null) {
                for (File file : files) {
                    out.println(file.getName());
                }
                out.println("end");
            }else System.out.println();
        }

        private void changeDirectory(String directory) {
            if (directory.equals("..")) {
            	if(cdFlag==0) {
            		  out.println("不允许访问根目录之上的目录" );
            		  out.println("end");
            		return;
            	}
                String parentDirectory = new File(rootDirectory).getParent();
                if (parentDirectory != null) {
                    rootDirectory = parentDirectory;
                    out.println("Directory changed to: " + rootDirectory);
                } else {
                    out.println("Cannot navigate beyond root directory");
                }
                cdFlag++;
            } else {
            	cdFlag--;
                File newDirectory = new File(rootDirectory, directory);
                if (newDirectory.isDirectory()) {
                    rootDirectory = newDirectory.getAbsolutePath();
                    out.println("Directory changed to: " + rootDirectory);
                } else {
                    out.println("Invalid directory");
                }
            }
            out.println("end");
        }

        private void uploadFile(String filename) {
            try {
            	String flag = in.readLine();
            	if(flag.equals("no")) {
            		return;
            	}
                File file = new File(rootDirectory, filename);
                FileOutputStream fos = new FileOutputStream(file);
                InputStream inputStream = clientSocket.getInputStream();

                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) > 0) {
                   
                    // 检查是否接收到了结束标记
                    if (isEndMarkerReceived(buffer, bytesRead)) {
                        break;
                    }
                    fos.write(buffer, 0, bytesRead);
                    fos.flush();
                }
                fos.write(buffer, 0, bytesRead-3);
                fos.flush();
                fos.close();
                System.out.println("上传成功");

                // 记录日志
                logger.info("File uploaded successfully\n"+"File uploaded: " + filename + ", User: " + username);
                 } catch (IOException e) {
                e.printStackTrace();
                out.println("Failed to upload file");

                // 记录日志
                logger.warning("Failed to upload file: " + filename + ", User: " + username);
            }
        }



        private void downloadFile(String filename) {
            try {
                File file = new File(rootDirectory, filename);

                if (!file.exists() || !file.isFile()) {
                    out.println("no");
                    return;
                }else {
                	out.println("ok");
                }

                FileInputStream fis = new FileInputStream(file);

                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    clientSocket.getOutputStream().write(buffer, 0, bytesRead);
                }
             // 发送结束标记
                String endMarker = "END";
                clientSocket.getOutputStream().write(endMarker.getBytes());

                fis.close();
                System.out.println("下载成功");

                // 记录日志
                logger.info("File downloaded successfully\n"+"File downloaded: " + filename + ", User: " + username);
            } catch (IOException e) {
                e.printStackTrace();
                out.println("Failed to download file");

                // 记录日志
                logger.warning("Failed to download file: " + filename + ", User: " + username);
            }
        }
    }
    private boolean isEndMarkerReceived(byte[] buffer, int bytesRead) {//判断文件是否结束
        String endMarker = "END";
        byte[] endMarkerBytes = endMarker.getBytes();

        // 检查接收到的字节数组是否与结束标记相同
        if (bytesRead >= endMarkerBytes.length) {
            for (int i = 0; i < endMarkerBytes.length; i++) {
                if (buffer[bytesRead - endMarkerBytes.length + i] != endMarkerBytes[i]) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }


    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}