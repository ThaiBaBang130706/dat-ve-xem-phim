package vn.cinema.server;

import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;

public final class ServerMain {
 private ServerMain(){}
 public static void main(String[] args)throws Exception {
  int tcpPort=5000,httpPort=5001;Path database=Path.of("data/cinema.db");boolean seed=true;
  for(String arg:args){
   if(arg.startsWith("--tcp-port="))tcpPort=Integer.parseInt(arg.substring(11));
   else if(arg.startsWith("--http-port="))httpPort=Integer.parseInt(arg.substring(12));
   else if(arg.startsWith("--db="))database=Path.of(arg.substring(5));
   else if(arg.equals("--no-seed"))seed=false;
   else if(arg.equals("--help")){System.out.println("java -jar cinema-server.jar [--tcp-port=5000] [--http-port=5001] [--db=data/cinema.db] [--no-seed]");return;}
   else throw new IllegalArgumentException("Tham số chưa hỗ trợ: "+arg);
  }
  ServerRuntime runtime=new ServerRuntime();
  Runtime.getRuntime().addShutdownHook(new Thread(runtime::close,"cinema-shutdown"));
  runtime.start(database,tcpPort,httpPort,seed);
  System.out.println("CinemaBooking | TCP "+runtime.tcpPort()+" | HTTP "+runtime.httpPort()+" | DB "+database.toAbsolutePath());
  System.out.println("Dashboard đọc khoá từ file dashboard.key bên cạnh database.");
  new CountDownLatch(1).await();
 }
}
