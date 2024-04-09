package client;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
public class clientport {
    private static final String serverAddress = "10.28.128.10";
    private BufferedReader in;  // 声明网络输入流
    private PrintWriter out; // 声明网络输出流
    private BufferedReader consoleReader; // 声明控制台读取流

    public static void main(String[] args) {
        clientport client = new clientport();
        client.start();
    }

    public void start(){
        try {
            Socket socket = new Socket(serverAddress, 8000);
            System.out.println("连接到服务器:" + socket.getInetAddress().getHostAddress());
            
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8")));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8")), true);
            consoleReader = new BufferedReader(new InputStreamReader(System.in));

            // 循环登录直到登录成功
            while(true){
                // 从控制台读取用户名并发送给服务器
                System.out.print("请输入用户名:");
                String username = consoleReader.readLine();
                out.println(username);
                // 非匿名用户登录读取密码
                if(!username.equals("anonymous")){
                    // 从控制台读取密码并发送给服务器
                    System.out.print("请输入密码:");
                    String password = consoleReader.readLine();
                    out.println(password);
                }
            
                // 接收服务器的响应
                String response = in.readLine();
                System.out.println("服务器响应: " + response);
                if(response.equals("登录成功")){
                    commandSelect(socket, username);
                    break;
                }
            }

            in.close();
            out.close();
            socket.close();
            System.out.println("与服务器断开连接");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 用户输入指令选择
    public void commandSelect(Socket socket, String username) throws IOException{
        String path = "服务端文件集";
        boolean running = true;
        while(running) {
            System.out.println("当前目录:"+path);
            System.out.println("请输入指令：");
            // 从控制台读取命令
            String command = consoleReader.readLine();
            out.println(command);
            // dir指令
            if(command.startsWith("dir")){
                String diresponse = in.readLine();
                while(!diresponse.isEmpty()){
                    System.out.println(diresponse);
                    diresponse = in.readLine();
                }
            }
            // cd指令
            else if(command.startsWith("cd")){
                String response = in.readLine();
                if(response.equals("cd指令执行成功")){
                    path = in.readLine();
                    System.out.println("cd指令执行成功!");
                }
                else{
                    System.out.println("cd指令执行错误!");
                }
            }
            // put指令
            else if(command.startsWith("put")){
                String filePath = command.substring(command.indexOf(" ") + 1);
                if(username.equals("anonymous")){
                    System.out.println("匿名用户无上传权限!");
                    out.println("匿名用户无上传权限");
                }
                else{
                    File file = new File(filePath);
                    if(file.exists() && file.isFile()){
                        out.println("文件存在且为有效文件");
                        // 调用putFile函数将文件上传
                        putFile(socket,file);
                    }
                    else{
                        System.out.println("文件不存在或非有效文件");
                        out.println("文件不存在或非有效文件");
                    }
                }
            }
            // get指令
            else if(command.startsWith("get")){
                String filename = in.readLine();
                getFile(filename, socket);
            }
            // exit指令
            else if(command.startsWith("exit")){
                running = false;
            }
        }
    }

    private void putFile(Socket socket, File file) throws IOException{
        String response = in.readLine();
        System.out.println(response);
        if(response.equals("服务端中已存在该文件，停止上传！")){
            return;
        }
        else{
            // 向客户端传输文件大小
            out.println(file.length());

            // 设置缓冲区大小
            byte[] buffer = new byte[2048];
            int bytesRead = 0;
            FileInputStream input = new FileInputStream(file);
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while ((bytesRead = input.read(buffer)) > 0) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
    
            response = in.readLine();
            System.out.println(response);
            input.close();
        }  
    }

    private void getFile(String fileName, Socket socket) throws IOException{
        InputStream inputStream = socket.getInputStream();
        BufferedInputStream input = new BufferedInputStream(inputStream);
        String fileSize = in.readLine();
        String currentPath = System.getProperty("user.dir");
        String filePath = currentPath + "\\" + fileName;
        long length = Long.parseLong(fileSize);
        byte[] fileContent = new byte[2048]; // 设置缓冲区大小
        int bytesRead = 0;
        long totalBytesRead = 0;
        FileOutputStream output = new FileOutputStream(filePath, false);
        while (totalBytesRead < length && (bytesRead = input.read(fileContent)) != -1) {
            output.write(fileContent, 0, bytesRead);
            totalBytesRead += bytesRead;
            if(totalBytesRead >= length || bytesRead < 2048){
                break;
            }
        }
        output.close();
        if (totalBytesRead == length) {
            out.println("文件下载成功!");
            System.out.println("文件下载成功");
        } else {
            out.println("文件下载失败!");
            System.out.println("文件下载失败");
        }
    }
}
