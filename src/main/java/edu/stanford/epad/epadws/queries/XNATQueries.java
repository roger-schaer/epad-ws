package edu.stanford.epad.epadws.queries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.dtos.XNATExperiment;
import edu.stanford.epad.dtos.XNATExperimentList;
import edu.stanford.epad.dtos.XNATProjectList;
import edu.stanford.epad.dtos.XNATSubject;
import edu.stanford.epad.dtos.XNATSubjectList;
import edu.stanford.epad.dtos.XNATUserList;
import edu.stanford.epad.epadws.handlers.search.EPADSearchFilter;
import edu.stanford.epad.epadws.xnat.XNATQueryUtil;

/**
 * Methods for querying XNAT
 * 
 * 
 * @author martin
 */
public class XNATQueries
{
	private static final EPADLogger log = EPADLogger.getInstance();

	public static XNATProjectList allProjects(String sessionID)
	{
		String allProjectsQueryURL = XNATQueryUtil.buildAllProjectsQueryURL();

		return invokeXNATProjectsQuery(sessionID, allProjectsQueryURL);
	}

	public static XNATUserList usersForProject(String sessionID, String projectID)
	{
		String allUsersForProjectQueryURL = XNATQueryUtil.buildAllUsersForProjectQueryURL(projectID);

		return invokeXNATUsersQuery(sessionID, allUsersForProjectQueryURL);
	}

	public static Set<String> patientNamesForProject(String sessionID, String projectID)
	{
		XNATSubjectList xnatSubjectList = XNATQueries.subjectsForProject(sessionID, projectID);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : xnatSubjectList.ResultSet.Result) {
			subjectIDs.add(subject.src);
		}
		return subjectIDs;
	}

	public static boolean filterSubject(XNATSubject xnatSubject, EPADSearchFilter searchFilter)
	{
		if (searchFilter.hasPatientNameMatch()) {
			String subjectName = xnatSubject.src;
			String patientNameMatch = searchFilter.getPatientNameMatch();
			String patientNameRegex = "\\.\\*" + patientNameMatch + "\\.\\*";
			if (subjectName.matches(patientNameRegex))
				return false;
			else
				return true;
		} else
			return false;
	}

	public static Set<String> patientIDsForProject(String sessionID, String projectID)
	{
		XNATSubjectList xnatSubjectList = XNATQueries.subjectsForProject(sessionID, projectID);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : xnatSubjectList.ResultSet.Result) {
			subjectIDs.add(subject.label);
		}
		return subjectIDs;
	}

	public static XNATSubjectList subjectsForProject(String sessionID, String projectID)
	{
		String allSubjectsForProjectQueryURL = XNATQueryUtil.buildAllSubjectsForProjectQueryURL(projectID);

		return invokeXNATSubjectsQuery(sessionID, allSubjectsForProjectQueryURL);
	}

	public static Set<String> subjectIDsForProject(String sessionID, String projectID)
	{
		XNATSubjectList xnatSubjectList = XNATQueries.subjectsForProject(sessionID, projectID);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : xnatSubjectList.ResultSet.Result) {
			subjectIDs.add(subject.ID);
		}
		return subjectIDs;
	}

	public static Set<String> studyUIDsForSubject(String sessionID, String projectID, String subjectID)
	{
		Set<String> studyIDs = new HashSet<String>();
		XNATExperimentList xnatExperiments = XNATQueries.getDICOMExperimentsForProjectAndSubject(sessionID, projectID,
				subjectID);

		for (XNATExperiment xnatExperiment : xnatExperiments.ResultSet.Result) {
			String studyID = xnatExperiment.label.replace("_", ".");
			studyIDs.add(studyID);
		}

		return studyIDs;
	}

	public static int numberOfSubjectsForProject(String sessionID, String projectID)
	{
		String allSubjectsForProjectQueryURL = XNATQueryUtil.buildAllSubjectsForProjectQueryURL(projectID);

		// TODO Need a count without getting all records.
		return invokeXNATSubjectsQuery(sessionID, allSubjectsForProjectQueryURL).ResultSet.totalRecords;
	}

	public static Set<String> subjectsIDForProject(String sessionID, String projectID)
	{
		String allSubjectsForProjectQueryURL = XNATQueryUtil.buildAllSubjectsForProjectQueryURL(projectID);
		XNATSubjectList subjectList = invokeXNATSubjectsQuery(sessionID, allSubjectsForProjectQueryURL);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : subjectList.ResultSet.Result) {
			subjectIDs.add(subject.label);
		}

		return subjectIDs;
	}

	public static XNATSubjectList subjectForProject(String sessionID, String projectID, String subjectName)
	{
		String projectSubjectQueryURL = XNATQueryUtil.buildProjectsSubjectQueryURL(projectID, subjectName);

		return invokeXNATSubjectsQuery(sessionID, projectSubjectQueryURL);
	}

	public static XNATExperimentList allDICOMExperiments(String sessionID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsQueryURL();

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	public static XNATExperimentList allDICOMExperimentsForProject(String sessionID, String projectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectQueryURL(projectID);

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	public static int numberOfDICOMExperimentsForProject(String sessionID, String projectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectQueryURL(projectID);

		// TODO Need a count without getting all records.
		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL).ResultSet.totalRecords;
	}

	public static XNATExperimentList getDICOMExperimentsForProjectAndSubject(String sessionID, String projectID,
			String subjectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectAndSubjectQueryURL(projectID,
				subjectID);

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	public static XNATExperimentList getDICOMExperimentsForProjectAndStudy(String sessionID, String projectID,
			String studyUID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectAndStudyUIDQueryURL(projectID,
				studyUID);

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	// TODO Fix this so that it makes a direct query with the subjectUID to XNAT
	public static XNATExperiment getDICOMExperimentForProjectAndSubjectAndStudy(String sessionID, String projectID,
			String subjectID, String studyUID)
	{
		XNATExperimentList xnatExperimentList = getDICOMExperimentsForProjectAndSubject(sessionID, projectID, subjectID);

		for (XNATExperiment xnatExperiment : xnatExperimentList.ResultSet.Result)
			if (xnatExperiment.label.equals(studyUID))
				return xnatExperiment;

		return null;
	}

	public static int numberOfDICOMExperimentsForProjectAndSubject(String sessionID, String projectID, String subjectID)
	{ // TODO Need a count without getting all records.
		return getDICOMExperimentsForProjectAndStudy(sessionID, projectID, subjectID).ResultSet.totalRecords;
	}

	public static int numberOfStudiesForProject(String sessionID, String projectID)
	{
		return XNATQueries.numberOfDICOMExperimentsForProject(sessionID, projectID);
	}

	public static int numberOfStudiesForSubject(String sessionID, String projectID, String subjectID)
	{
		return XNATQueries.numberOfDICOMExperimentsForProjectAndSubject(sessionID, projectID, subjectID);
	}

	private static XNATProjectList invokeXNATProjectsQuery(String sessionID, String xnatProjectsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatProjectsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			log.info("Invoking XNAT query at " + xnatProjectsQueryURL);
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT projects query", e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATProjectsQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATUserList invokeXNATUsersQuery(String sessionID, String xnatUsersQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatUsersQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			log.info("Invoking XNAT query at " + xnatUsersQueryURL);
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT projects query", e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATUsersQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATUserList processXNATUsersQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATUsersFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT projects query");
				return XNATUserList.emptyUsers();
			} else {
				log.warning("Error performing XNAT projects query; XNAT status code = " + xnatStatusCode);
				return XNATUserList.emptyUsers();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATProjectList processXNATProjectsQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATProjectsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT projects query");
				return XNATProjectList.emptyProjects();
			} else {
				log.warning("Error performing XNAT projects query; XNAT status code = " + xnatStatusCode);
				return XNATProjectList.emptyProjects();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATUserList extractXNATUsersFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATUserList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT users query result", e);
			return XNATUserList.emptyUsers();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT users query result", e);
			return XNATUserList.emptyUsers();
		} finally {
			IOUtils.closeQuietly(isr);
			IOUtils.closeQuietly(br);
			method.releaseConnection();
		}
	}

	private static XNATProjectList extractXNATProjectsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATProjectList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT projects query result", e);
			return XNATProjectList.emptyProjects();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT projects query result", e);
			return XNATProjectList.emptyProjects();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}

	private static XNATSubjectList invokeXNATSubjectsQuery(String sessionID, String xnatSubjectsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatSubjectsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			log.info("Invoking XNAT query at " + xnatSubjectsQueryURL);
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Warning: error performing XNAT subject query", e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATSubjectQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATSubjectList processXNATSubjectQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATSubjectsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT subjects query");
				return XNATSubjectList.emptySubjects();
			} else {
				log.warning("Error performing XNAT subjects query; XNAT status code = " + xnatStatusCode);
				return XNATSubjectList.emptySubjects();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATSubjectList extractXNATSubjectsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATSubjectList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT subjects query result", e);
			return XNATSubjectList.emptySubjects();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT subjects query result", e);
			return XNATSubjectList.emptySubjects();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}

	private static XNATExperimentList invokeXNATDICOMExperimentsQuery(String sessionID,
			String xnatDICOMExperimentsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatDICOMExperimentsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			log.info("Invoking XNAT query at " + xnatDICOMExperimentsQueryURL);
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT experiment query", e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATExperimentsQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATExperimentList processXNATExperimentsQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATExperimentsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT experiments query");
				return XNATExperimentList.emptyExperiments();
			} else {
				log.warning("Error performing XNAT experiments query; XNAT status code = " + xnatStatusCode);
				return XNATExperimentList.emptyExperiments();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATExperimentList extractXNATExperimentsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATExperimentList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT experiments query result", e);
			return XNATExperimentList.emptyExperiments();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT experiments query result", e);
			return XNATExperimentList.emptyExperiments();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}
}