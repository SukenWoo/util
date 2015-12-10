import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class SwitchRouter {
  static Logger logger = Logger.getLogger(SwitchRouter.class);

  static ApplicationContext ctx;

  private String router0="192.168.1.1";

  private String router1="192.168.20.1";

  private String[] hosts = {"1.2.4.8", "8.8.8.8"};

  private long[] prev = new long[hosts.length];

  private int switchPeriod = 3600; 

  private int pingCount = 5;

  public SwitchRouter() {
  }


  public void setRouter0(String router0) {
    this.router0 = router0;
  }

  public void setRouter1(String router1) {
    this.router1 = router1;
  }

  public void setHosts(String[] hosts) {
    this.hosts = hosts;
    this.prev = new long[this.hosts.length];
  }

  public void setSwitchPeriod(int switchPeriod) {
    this.switchPeriod = switchPeriod;
  }


  public void setPingCount(int pingCount) {
    this.pingCount = pingCount;
  }


  public void run() {
    while(true){
      for(int i=0;i<hosts.length;i++) {
        prev[i] = checkSwitch(hosts[i], prev[i]);
        try {
          Thread.sleep(1);
        }
        catch(Exception ex){}
      }
    }
  }

  public long checkSwitch(final String host, final long prev) {
    long ret = prev;
    long ct = System.currentTimeMillis();
    boolean bswitch = isSwitch(host);
    addRouter(1, host, router0);
    boolean ping0 = ping(1, host, pingCount);
    deleteRouter(1, host);
    boolean ping1 = ping(1, host, pingCount);
    if(bswitch){
      if(ct-prev > switchPeriod*1000) {
        if(ping0 == true) {
          deleteRouter(0, host);
          logger.info(host+" switch(timeout) back to "+router0);
        }
      }
      else if(ping0 == true && ping1 == false) {
        deleteRouter(0, host);
        logger.info(host+" switch back to "+router0);
      }
    }
    else {
      if(ping0 == false && ping1 == true) {
        addRouter(0, host, router1);
        ret = ct;
        logger.info(host+" switch to "+router1);
      }
    }
    return ret;
  }

  public boolean isSwitch(final String host) {
    int ret = -1;
    final Vector<Boolean> found = new Vector<Boolean>();
    try {
      String[] cmdArray = {"netstat", "-4ra"};
      final Process process = new ProcessBuilder(cmdArray).
          redirectErrorStream(true).start();
      Thread thr1 = new Thread(){
        public void run(){
          try {
            BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line = null;
            while ( (line=bufferedReader.readLine()) != null) {
              if(line.trim().startsWith(host)){
                found.add(true);
                break;
              }
            }
            bufferedReader.close();          
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      };
      thr1.start();
      ret = process.waitFor();
    } catch (Exception e) {
      logger.warn(e.getMessage(), e);
      return false;
    }
    return ret == 0 && found.size() != 0;
  }

  public boolean ping(int fib, String host, int count){
    int ret = -1;
    try {
      String[] cmdArray = {"setfib", ""+fib, "ping", "-qc" ,""+count, host};
      final Process process = new ProcessBuilder(cmdArray).start();
      ret = process.waitFor();
      process.destroy();
    } catch (Exception ex) {
      logger.warn(ex.getMessage(), ex);
    }
    return ret == 0;
  }

  public boolean addRouter(int fib, String host, String router) {
    int ret = -1;
    try {
      String[] cmdArray = {"setfib", ""+fib, "route", "add", "-host", host, router};
      final Process process = new ProcessBuilder(cmdArray).start();
      ret = process.waitFor();
      process.destroy();
    } catch (Exception ex) {
      logger.warn(ex.getMessage(), ex);
    }
    return ret == 0;

  }

  public boolean deleteRouter(int fib, String host) {
    int ret = -1;
    try {
      String[] cmdArray ={"setfib", ""+fib, "route", "delete", "-host", host};
      final Process process = new ProcessBuilder(cmdArray).start();
      ret = process.waitFor();
      process.destroy();
    } catch (Exception ex) {
      logger.warn(ex.getMessage(), ex);
    }
    return ret == 0;
  }



  public static void main(String[] args) {
    cn.org.gddsn.util.Log4jConfig.loadForMain("SwitchRouter.properties");    
    ctx = new FileSystemXmlApplicationContext(new String[] { "SwitchRouter.xml" });
    SwitchRouter sr = (SwitchRouter)ctx.getBean("switchRouter");
    //SwitchRouter sr = new SwitchRouter();
    sr.run();
  }
}
