package site.conghucai.config;

import io.netty.handler.ssl.ClientAuth;
import lombok.Data;

@Data
public class SSLConfig extends RegistryConfig {

    private boolean useSSL = true;    //是否开启ssl认证
    private String keyPath = "classpath:ssl/server_pk8.key";  //服务器私钥
    private String keyPwd = "980213";   //私钥访问密码
    private String certPath = "classpath:ssl/server.crt"; //服务器端自签名的私有安全证书
    private String trustCertPath = null;    //受信任的CA安全证书
    private ClientAuth clientAuth = ClientAuth.NONE;    //客户端认证模式  缺省值为None  服务器单向

    public SSLConfig() {
    }
}
