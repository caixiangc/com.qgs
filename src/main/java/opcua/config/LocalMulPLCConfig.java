package opcua.config;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import opcua.constant.CommonFunction;
import opcua.constant.PLCConstant;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;


public class LocalMulPLCConfig {

    //读取json文件
    public static String readJsonFile(String fileName) {
        String jsonStr = "";
        try {
            File jsonFile = new File(fileName);
            if (!jsonFile.exists()) {
                boolean b= jsonFile.createNewFile();
                JSONObject json = new JSONObject();
                json =JSON.parseObject("{\"config\": []}");
                FileWriter fw = new FileWriter(jsonFile.getAbsoluteFile());
                BufferedWriter bw = new BufferedWriter(fw);
                json.writeJSONString(bw);
                bw.close();
            }

            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8);
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    public static HashMap<String,PLCConfig> getPLCConfig(){
        //String path = LocalMulPLCConfig.class.getClassLoader().getResource("mulPLCConfig.json").getPath();
        String s = "";
        if(CommonFunction.isLinux()){
            CommonFunction.createDirIfNotExit(PLCConstant.localURLDirForLinux);
            s = readJsonFile(PLCConstant.localURLForLinux);
        }else if(CommonFunction.isWindows()){
            CommonFunction.createDirIfNotExit(PLCConstant.localURLDirForWindows);
            s = readJsonFile(PLCConstant.localURLForWindows);
        }

        JSONObject jobj = JSON.parseObject(s);



        JSONArray movies = jobj.getJSONArray("config");//构建JSONArray数组

        HashMap<String,PLCConfig> res = new HashMap<>();
        for (int i = 0 ; i < movies.size();i++){
            JSONObject key = (JSONObject)movies.get(i);
            String plcName = (String)key.get("plc_name");
            String url_config = (String)key.get("endpointUrl_config");
            String policy_config = (String)key.get("securityPolicy_config");
            String[] user_config =((String)key.get("identityProvider_config")).isEmpty()?null:((String)key.get("identityProvider_config")).split(",");
            String ip = (String)key.get("ip");
            Integer messageMode = (Integer)key.get("messageMode_config");
            res.put(plcName,new PLCConfig(plcName,url_config, SecurityPolicy.valueOf(policy_config),user_config,ip,messageMode));
        }
        return res;
    }
}
