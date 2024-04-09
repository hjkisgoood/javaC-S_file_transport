package client;

/*在Java客户端和服务器之间传输文件时，可以使用一些方法来判断文件传输的结束。以下是一些常见的方法：

文件大小固定：在传输文件之前，可以先确定文件的大小，并在接收端判断是否接收到了完整的文件内容。比较传输的字节数与文件的大小，如果相等，则可以认为文件传输完成。

文件尾标记：可以在文件的末尾添加一个特定的标记作为结束符。在接收端，通过检测是否接收到了该结束符来判断文件传输是否完成。

预定的数据包长度：在传输过程中，可以约定每个数据包的固定长度。当接收到长度小于预定长度的数据包时，可以认为文件传输完成。

定义协议：可以在传输文件的过程中定义一个协议，规定数据包的格式和传输规则。协议可以包括文件大小信息、数据包编号、校验和等字段，通过协议中的规定来判断文件传输是否完成。

以上方法都需要在客户端和服务器端的代码中进行相应的逻辑判断和处理。具体选择哪种方法取决于你的需求和设计。请根据你的具体情况选择适合的方法来判断文件传输的结束。*/

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Scanner;

public class Client {
    private static final String SERVER_IP = "26.44.71.73";
    private static final int SERVER_PORT = 8845;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
            System.out.println("成功连接到服务器");
            int putPermission = 1;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            String username;
            String password;
            
            while(true) {
	            System.out.print("请输入用户名：");
	            username = scanner.nextLine().trim();
	
	            if (!username.equalsIgnoreCase("anonymous")) {
	                System.out.print("请输入密码：");
	                password = scanner.nextLine().trim();
	                out.println(username);
	                out.println(password);
	            } else {
	            	putPermission = 0;
	                out.println(username);
	            }
	
	            String response = in.readLine();
	            if (response.startsWith("Authentication successful")) {
	            	break;
	            }
	            System.out.println(response);
            }


            System.out.println("成功登录");
            System.out.println("客户端当前工作目录：" +System.getProperty("user.dir")+"/sources");

        	


            while (true) {
            	System.out.println("请输入命令：");
                String command = scanner.nextLine().trim();
                out.println(command);

                if (command.equalsIgnoreCase("exit")) {
                	System.out.println("退出成功");
                    break;
                }
                
                if (command.toLowerCase().startsWith("get")) {
                    downloadFile(socket, command,in);
                } else if (command.toLowerCase().startsWith("put")) {
                    uploadFile(socket, command,out,putPermission);
                }else if (command.equalsIgnoreCase("dir")) {
                    listFiles(in);
                } else if (command.toLowerCase().startsWith("cd")) {
                    changeDirectory(in, command);
                }else {
                	System.out.println("输入不合法");
                }
            }
            
            scanner.close();
        } catch (IOException e) {
        	System.out.println("????");
            e.printStackTrace();
            
        }
        
       
    } 

    private static void downloadFile(Socket socket, String command, BufferedReader in) {
        try {
        	String flag = in.readLine();
        	if(flag.equals("no")) {
        		System.out.println("文件不存在");
        		return;
        	}

            String[] tokens = command.split(" ");
            String filename = tokens[1];

            Path filePath = Path.of("sources/"+filename);
            FileOutputStream fos = new FileOutputStream(filePath.toFile());
            byte[] buffer = new byte[1024];
            int bytesRead;

            InputStream inputStream = socket.getInputStream();

           while ((bytesRead = inputStream.read(buffer)) != -1) {
            	// 检查是否接收到了结束标记
                if (isEndMarkerReceived(buffer, bytesRead)) {
                    break;
                }
                fos.write(buffer, 0, bytesRead);
            }
            fos.write(buffer, 0, bytesRead-3);

            fos.close();
            System.out.println("文件下载成功");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("文件下载失败");
        }
    }

    private static void uploadFile(Socket socket, String command,PrintWriter out,int putPermission) {
        try {
            String[] tokens = command.split(" ");
            String filename = tokens[1];

            File file = new File("sources/"+filename);
            if (!file.exists() || !file.isFile()||putPermission==0) {
                System.out.println("文件不存在或者不允许访问");
                out.println("no");
                return;
            }else {
            	out.println("okay");
            }

            OutputStream outputStream = socket.getOutputStream();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) >0) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }       
         // 发送结束标记
            String endMarker = "END";
            outputStream.write(endMarker.getBytes());
            outputStream.flush();
            fis.close();
            System.out.println("文件上传成功");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("文件上传失败");
        }
        
    }
    
    private static void listFiles(BufferedReader in) {
        try {
            String response;
            while (!(response = in.readLine()).equals("end")) {
                System.out.println(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("无法获取文件列表");
        }
    }

    private static void changeDirectory(BufferedReader in, String command) {
        try {
//            String response = in.readLine();
//            System.out.println(response);
        	String response;
        	 while (!(response = in.readLine()).equals("end")) {
                 System.out.println(response);
             }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("无法更改目录");
        }
    }

    private static boolean isEndMarkerReceived(byte[] buffer, int bytesRead) {//判断文件是否结束
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

}