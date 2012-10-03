import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.auth.AuthScope;
import org.apache.http.util.EntityUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpHost;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import java.text.SimpleDateFormat;

/********************CONFIGURATIONS****************************/

// Recipients should be specified here for automatic tools. 
// Recipients can be users or groups created in OpsGenie
RECIPIENTS="all"
SOURCE="OpenNMS"


//OpenNMS credentials are needed for extra information to be fetched through REST API
OPENNMS_USER = "admin"
OPENNMS_PASSWORD = "admin"
OPENNMS_HOST="localhost"
OPENNMS_PORT=8980


def nodeId = params.nodeId;

if(nodeId){
	def alertProps = [:]
	alertProps.message = params.subject
	alertProps.recipients = RECIPIENTS
	alertProps.description = params.textMessage
	alertProps.source = SOURCE
	
	
	logger.warn("Creating alert with message ${alertProps.message}");
	println "Creating alert"
	def response = opsgenie.createAlert(alertProps)
	def alertId =  response.alertId;
	logger.warn("Alert is created with id :"+alertId);
	println "Alert is created with id :"+alertId
	attach(nodeId, alertId)
}
else{
	logger.warn("No node id is specified, skipping...")
	println "No node id is specified, skipping..."
}

def attach(nodeId, alertId){
	def restResponse = restCall(nodeId);
	if(restResponse){
		String htmlText = createHtml(restResponse);
		if(htmlText){
			logger.warn("Attaching details");
		    println "Attaching details"
		    response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(htmlText.getBytes()), fileName:"outages.html"])
		    if(response.success){
		        logger.warn("Successfully attached details");
		        println "Successfully attached details"
		    }
		    else{
		        println "Could not attach details"
		        logger.warn("Could not attach details");
		    }		
		}	
		else{
			logger.warn("No outages found, skipping attachment.")
			println "No outages found, skipping attachment."
		}    
	}
}

def createHtml(restResponse){
	def xml = new XmlSlurper().parseText(restResponse);
	def outages = xml.outage;
	if(outages.size() > 0){
	    def nodelabel = outages[0].serviceLostEvent.nodeLabel.text();
	    //change these if you have different date formats.
	    def dateParser = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
	    def dateFormatter = new SimpleDateFormat("M/dd/yy hh:mm:ss a");
		StringBuffer buf = new StringBuffer();
		buf.append("""
			<html>
				<head>
					<style>
						body{background:#eee;}
						table{border-collapse: collapse;background:white;width: 100%;}
						td{border: 1px solid #999999;padding: 4px 5px;vertical-align:top;}
						.outage, .event{font-weight:bold;line-height:1.25em}
						.outage{background:#66992A;color:white;}
						.event{background:#FFEBCD;color:#555;}
					</style>
				</head>
				<body>
					<div>
						<h2>Outages of Node ${nodelabel}</h2>
						<table>
							<tbody>
					
		""")
		
		outages.each{
			def outageId = it.@id.text();
			def serviceLostEvent = it.serviceLostEvent
			def serviceRegainedEvent = it.serviceRegainedEvent
			def ipInterface = it.ipAddress.text();
			def service = it.monitoredService.serviceType.name.text();
			def lostTime = it.ifLostService.text();
			if(lostTime.indexOf("+") > -1){
				lostTime = lostTime.substring(0, lostTime.indexOf("+"))
			}
			lostTime = dateFormatter.format(dateParser.parse(lostTime));
			def regainTime = it.ifRegainedService.text();
			if(regainTime){
				if(regainTime.indexOf("+") > -1){
					regainTime = regainTime.substring(0, regainTime.indexOf("+"))
				}
				regainTime = dateFormatter.format(dateParser.parse(regainTime));
			}
			else{
				regainTime = "DOWN"
			}
			buf.append("""
				<tr>
					<td colspan="4" class="outage">Outage: ${outageId}</td>
				</tr>
				<tr>
					<td>Interface:</td>
					<td>${ipInterface}</td>
					<td>Lost Service Time:</td>
					<td>${lostTime}</td>
				</tr>
				<tr>
					<td>Service:</td>
					<td>${service}</td>
					<td>Regain Service Time:</td>
					<td>${regainTime}</td>
				</tr>
				<tr>
					<td colspan="4" class="event">Service Lost Event</td>
				</tr>
				<tr>
					<td>Severity:</td>
					<td>${serviceLostEvent.@severity.text()}</td>
					<td>UEI:</td>
					<td>${serviceLostEvent.uei.text()}</td>
				</tr>
				<tr>
					<td>Description:</td>
					<td colspan="3">${serviceLostEvent.description.text()}</td>
				</tr>
				<tr>
					<td>Log Message:</td>
					<td colspan="3">${serviceLostEvent.logMessage.text()}</td>
				</tr>
			""")
			if(serviceRegainedEvent.size() > 0){
				buf.append("""
					<tr>
						<td colspan="4" class="event">Service Regained Event</td>
					</tr>
					<tr>
						<td>Severity:</td>
						<td>${serviceRegainedEvent.@severity.text()}</td>
						<td>UEI:</td>
						<td>${serviceRegainedEvent.uei.text()}</td>
					</tr>
					<tr>
						<td>Description:</td>
						<td colspan="3">${serviceRegainedEvent.description.text()}</td>
					</tr>
					<tr>
						<td>Log Message:</td>
						<td colspan="3">${serviceRegainedEvent.logMessage.text()}</td>
					</tr>
				""")
			}
		}
		
		buf.append("""
							</tbody>
						</table>
					</div>
				</body>
			</html>
		""")
		return buf.toString();
	}
	return null;
}

def restCall(nodeId){
	def timeout = 30000;
	logger.warn("Getting node outages");
    println "Getting node outages"
	HttpHost targetHost = new HttpHost(OPENNMS_HOST, OPENNMS_PORT, "http");
	HttpParams httpClientParams = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
    HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
    HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
	DefaultHttpClient httpclient = new DefaultHttpClient(httpClientParams);
	try {
		AuthScope scope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
		httpclient.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(OPENNMS_USER, OPENNMS_PASSWORD));
		HttpGet httpGet = new HttpGet("/opennms/rest/outages/forNode/${nodeId}/");
		
		def response = httpclient.execute(targetHost, httpGet);
		if(response.getStatusLine().getStatusCode() == 200){
			logger.warn("Node outages received");
        	println "Node outages received"	
			return EntityUtils.toString(response.getEntity());
		}
		else{
			logger.warn("Could not get node outages")
			println "Could not get node outages"
			return null;
		}
	}
	finally{
		httpclient.getConnectionManager().shutdown();
	}
	
}