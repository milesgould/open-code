package com.winterwell.datalog.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.utils.Printer;
import com.winterwell.utils.StrUtils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.web.WebUtils2;
import com.winterwell.web.app.AppUtils;
import com.winterwell.web.app.BrowserType;
import com.winterwell.web.app.FileServlet;
import com.winterwell.web.app.KServerType;
import com.winterwell.web.app.WebRequest;
import com.winterwell.web.app.WebRequest.KResponseType;
import com.winterwell.web.fields.BoolField;
import com.winterwell.web.fields.JsonField;
import com.winterwell.web.fields.SField;


/**
 * Fast Ajax logging of stats.
 * 
 * Endpoint: /lg <br>
 * Parameters: <br>
 *  - tag Optional. Will have log prepended, so we can distinguish ajax-logged events (which could be bogus!) 
 * from internal ones. E.g. "foo" gets written as "#log.foo" <br>
 *  - msg
 * 
 * @see AServlet
 * <p>
 * TODO filter by time
 * @author daniel
 *
 */
public class LgServlet {

	static final SField TAG = new SField("t");
	public static final SField DATASPACE = new SField("d");

	public LgServlet() {		
	}
		
	
	static JsonField PARAMS = new JsonField("p");
	
	/**
	 * Log msg to fast.log file.  
	 * @param req
	 * @param resp
	 * @throws IOException 
	 */
	public static void fastLog(WebRequest state) throws IOException {
		HttpServletRequest req = state.getRequest();
		HttpServletResponse resp = state.getResponse();
		String u = state.getRequestUrl();
		Map<String, Object> ps = state.getParameterMap();
		String ds = state.getRequired(DATASPACE);
		// TODO security check the dataspace?
		String tag = state.getRequired(TAG);
		String via = req.getParameter("via");
		// NB: dont IP/user track simple events, which are server-side
		boolean stdTrackerParams = ! DataLogEvent.simple.equals(tag) && state.get(new BoolField("track"), true);
		Map params = (Map) state.get(PARAMS);
		if (params==null) {
			// params from the url?
			// e.g. 
			// https://lg.good-loop.com/lg?d=gl&t=install&idfa={idfa}&adid={adid}&android_id={android_id}&gps_adid={gps_adid}
			// &fire_adid={fire_adid}&win_udid={win_udid}&ua={user_agent}&ip={ip_address}&country={country}
			// &time={created_at}&app_id={app_id}&app_name={app_name}&store={store}&tracker_name={tracker_name}&tracker={tracker}
			// &bid={dcp_bid}
			params = state.getMap();
			params.remove(TAG);
			params.remove(DATASPACE);
			params.remove("via");
			params.remove("track");
		}
		
		boolean logged = doLog(state, ds, tag, via, params, stdTrackerParams);
		
		// Reply
		// .gif?
		if (state.getResponseType()==KResponseType.image) {
			FileServlet.serveFile(TrackingPixelServlet.PIXEL, state);
			return;
		}
		if (DataLogServer.settings.CORS) {
			WebUtils2.CORS(state, false);
		}
		WebUtils2.sendText(logged? "OK" : "not logged", resp);
	}

	static boolean doLog(WebRequest state, String dataspace, String tag, String via, Map params, boolean stdTrackerParams) {
		assert dataspace != null;
		String trckId = TrackingPixelServlet.getCreateCookieTrackerId(state);
		// special vars
		if (stdTrackerParams) {
			// TODO allow the caller to explicitly set some of these if they want to
			if (params==null) params = new ArrayMap();
			// Replace $user with tracking-id, and $
			params.putIfAbsent("user", trckId);			
			// ip: $ip
			params.putIfAbsent("ip", state.getRemoteAddr());
			// Browser info
			String ua = state.getUserAgent();			
			params.putIfAbsent("ua", ua);
			BrowserType bt = new BrowserType(ua);
			boolean mobile = bt.isMobile();
			params.putIfAbsent("mbl", mobile);
			// what page?
			String ref = state.getReferer();
			if (ref==null) ref = state.get("site"); // DfP hack
			// remove some gumpf (UTM codes)
			String cref = WebUtils2.cleanUp(ref);
			if (cref != null) {
				params.putIfAbsent("url", cref);
				// domain (e.g. sodash.com) & host (e.g. www.sodash.com)				
				params.putIfAbsent("domain", WebUtils2.getDomain(cref)); 
				// host is the one to use!
				params.putIfAbsent("host", WebUtils2.getHost(cref)); // matches publisher in adverts
			}
		}
		
		// HACK remove Hetzner from the ip param 
		// TODO make this a config setting?? Or even better, the servers report their IP
		Object ip = params.get("ip");
		if (ip instanceof String) ip = ((String) ip).split(",\\s*");
		List<Object> ips = Containers.list(ip);
		if (ips.contains("5.9.23.51")) {
			ips = Containers.filter(ips, a -> ! "5.9.23.51".equals(a));
			if (ips.size() == 1) {
				params.put("ip", ips.get(0));
			} else {
				params.put("ip", ips);
			}
		}
		
		// screen out our IPs?
		if ( ! accept(dataspace, tag, params)) {
			return false;
		}
		
		// write to log file
		doLogToFile(dataspace, tag, params, trckId, via, state);
				
		// write to Stat / ES
		// ...which dataspaces?
		// Multiple dataspaces: Dan A reports a significant cost to per-user dataspaces
		// -- he estimated one server per 4k ES indexes. c.f. #5403
		// Info should be stored to named dataspace + user + publisher + advertiser
		// TODO upgrade DatalogEvent to have several dataspaces??
//		ArraySet<String> dataspaces = new ArraySet(
//			dataspace, params.get("user") // publisher, advertiser			
//		);
//		for(String ds : dataspaces) {
//			if (ds==null) continue;
		DataLogEvent event = new DataLogEvent(dataspace, 1, tag, params);		
		DataLog.count(event);
//		}
		return true;
	}

	
	/**
	 * HACK screen off our IPs and test sites
	 * 
	 * TODO instead do this by User, and have a no-log parameter in the advert
	 * 
	 * @param dataspace2
	 * @param tag2
	 * @param params2
	 * @return
	 */
	private static boolean accept(String dataspace, String tag, Map params) {
		KServerType stype = AppUtils.getServerType(null);
		// only screen our IPs out of production
		if (stype != KServerType.PRODUCTION) 
		{
			return true;
		}
		if ( ! "gl".equals(dataspace)) return true;
		Object ip = params.get("ip");
		List<String> ips = Containers.list(ip);		
		if ( ! Collections.disjoint(OUR_IPS, ips)) {
			Log.d("lg", "skip ip "+ip+" event: "+tag+params);
			return false;
		}
		if ("good-loop.com".equals(params.get("host"))) {
			String url = (String) params.get("url");
			// do track the marketing site, esp live demo, but otherwise no GL sites 
			if (url!=null) {
				if (url.contains("live-demo")) return true;
				if (url.contains("//www.good-loop.com")) return true;
				if (url.contains("//good-loop.com")) return true;
			}
			Log.d("lg", "skip url "+url+" event: "+tag+params);
			return false;
		}
		return true;
	}

	static List<String> OUR_IPS = Arrays.asList("62.30.12.102", "62.6.190.196", "82.37.169.72");
	
	private static void doLogToFile(String dataspace, String tag, Map params, String trckId, String via, WebRequest state) {
		String msg = params == null? "" : Printer.toString(params, ", ", ": ");
		msg += "\ttracker:"+trckId+"\tref:"+state.getReferer()+"\tip:"+state.getRemoteAddr();
		if (via!=null) msg += " via:"+via;
		// Guard against giant objects getting put into log, which is almost
		// certainly a careless error
		if (msg.length() > Log.MAX_LENGTH) {
			msg = StrUtils.ellipsize(msg, Log.MAX_LENGTH);
//			error = StrUtils.ellipsize(msg, 140)+" is too long for Log!";
		}
		// chop #tag down to tag (including embedded #, as in tr_#myapp)
		tag = tag.replace("#", "");
		tag = dataspace+"."+tag;
		// Note: LogFile will force the report onto one line by converting [\r\n] to " "
		// Add in referer and IP
		// Tab-separating elements on this line is useless, as Report.toString() will immediately convert \t to space.
		String msgPlus = msg+" ENDMSG "+state.getReferer()+" "+state.getRemoteAddr();
//		Report rep = new Report(tag, null, msgPlus, Level.INFO);
		Log.i(tag, msgPlus);
//		DataLogServer.logFile.listen2(rep.toStringShort(), rep.getTime());
	}

}
