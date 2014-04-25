import com.ifountain.client.http.HttpClient
import com.ifountain.client.http.HttpClient
import com.ifountain.client.util.ClientConfiguration
import org.apache.http.HttpHost

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");
CONF_PREFIX = "nagios.";
def alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
if (alertFromOpsgenie.size() > 0) {

    def host = alertFromOpsgenie.details.host
    def service = alertFromOpsgenie.details.service
    def postParams = ["btnSubmit": "Commit", "cmd_mod": "2", "send_notification": "off", "host": host]
    if(service) postParams.service = service;
    boolean discardAction = false;
    if(action == "Acknowledge"){
        if(source != null && source.name == "nagios"){
            logger.warn("OpsGenie alert is already acknowledged by nagios. Discarding!!!");
            discardAction = true;
        }
        else{
            postParams.com_data = "Acknowledged by ${alert.username} via OpsGenie"
            postParams.sticky_ack = "on"
            postParams.cmd_typ = service ? "34" : "33";
        }
    }
    else if(action == "TakeOwnership"){
        postParams.com_data = "alert ownership taken by ${alert.username}"
        postParams.cmd_typ = service ? "3" : "1";
    }
    else if(action == "AssignOwnership"){
        postParams.com_data = "alert ownership assigned to ${alert.owner}"
        postParams.cmd_typ = service ? "3" : "1";
    }
    else if(action == "AddNote"){
        postParams.com_data = "${alert.note} by ${alert.username}"
        postParams.cmd_typ = service ? "3" : "1";
    }
    def nagiosServer=alertFromOpsgenie.details.nagiosServer
    if (!nagiosServer || nagiosServer == "default")
    {
        CONF_PREFIX = "nagios.";
    }
    else
    {

        CONF_PREFIX = "nagios."+nagiosServer+".";
    }
    if(!discardAction){
        postToNagios(postParams);
    }

}
else {
    logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if(timeout == null){
        timeout = 30000;
    }
    else{
        timeout = timeout.toInteger();
    }

    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout);
    return new HttpClient(clientConfiguration)
}
def _conf(confKey, boolean isMandatory)
{
    def confVal = conf[CONF_PREFIX+confKey]
    if(isMandatory && confVal == null){
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX+confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}

def getUrl() {
    def url = _conf("command_url", true)
    if (url != null) {
        return url;
    } else {
        //backward compatability
        def scheme = _conf("http.scheme", false)
        if (scheme == null) scheme = "http";
        def port = _conf("port", true).toInteger();
        def host = _conf("host", true);
        return new HttpHost(host, port, scheme).toURI() + "/nagiosxi/includes/components/nagioscore/ui/cmd.php";
    }
}

def postToNagios(Map postParams){
    HttpClient HTTP_CLIENT = createHttpClient();
    try{
        String url = getUrl();
        url += "?username=" + _conf("user", true)
        url += "&ticket=" + _conf("ticket", true)
        logger.debug("${LOG_PREFIX} Posting to Nagios. Url ${url} params:${postParams}")
        def response = ((HttpClient) HTTP_CLIENT).post(url, postParams)
        if (response.getStatusCode() == 200) {
            logger.info("${LOG_PREFIX} Successfully executed at Nagios.");
            logger.debug("${LOG_PREFIX} Nagios response: ${response.getContentAsString()}")
        } else {
            logger.warn("${LOG_PREFIX} Could not execute at Nagios. Nagios Resonse:${response.getContentAsString()}")
        }
    }finally {
        HTTP_CLIENT.close();
    }

}



