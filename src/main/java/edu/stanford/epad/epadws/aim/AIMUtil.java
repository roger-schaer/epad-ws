package edu.stanford.epad.epadws.aim;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.SequenceAttribute;
import com.pixelmed.dicom.SequenceItem;
import com.pixelmed.dicom.TagFromName;

import edu.stanford.epad.common.pixelmed.PixelMedUtils;
import edu.stanford.epad.common.plugins.PluginAIMUtil;
import edu.stanford.epad.common.plugins.PluginConfig;
import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.common.util.XmlNamespaceTranslator;
import edu.stanford.epad.dtos.EPADAIM;
import edu.stanford.epad.dtos.EPADAIMList;
import edu.stanford.epad.dtos.EPADAIMList.EPADAIMResultSet;
import edu.stanford.epad.dtos.internal.DICOMElement;
import edu.stanford.epad.dtos.internal.DICOMElementList;
import edu.stanford.epad.epadws.aim.aimapi.Aim;
import edu.stanford.epad.epadws.aim.aimapi.Aim4;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabaseOperations;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.handlers.core.FrameReference;
import edu.stanford.epad.epadws.handlers.core.ImageReference;
import edu.stanford.epad.epadws.handlers.core.ProjectReference;
import edu.stanford.epad.epadws.processing.pipeline.task.PluginStartTask;
import edu.stanford.epad.epadws.queries.Dcm4CheeQueries;
import edu.stanford.epad.epadws.queries.DefaultEpadOperations;
import edu.stanford.epad.epadws.queries.EpadOperations;
import edu.stanford.epad.epadws.queries.XNATQueries;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;
import edu.stanford.hakan.aim3api.base.AimException;
import edu.stanford.hakan.aim3api.base.DICOMImageReference;
import edu.stanford.hakan.aim3api.base.GeometricShape;
import edu.stanford.hakan.aim3api.base.GeometricShapeCollection;
import edu.stanford.hakan.aim3api.base.ImageAnnotation;
import edu.stanford.hakan.aim3api.base.Person;
import edu.stanford.hakan.aim3api.base.Segmentation;
import edu.stanford.hakan.aim3api.base.SegmentationCollection;
import edu.stanford.hakan.aim3api.base.SpatialCoordinate;
import edu.stanford.hakan.aim3api.base.TwoDimensionSpatialCoordinate;
import edu.stanford.hakan.aim3api.base.User;
import edu.stanford.hakan.aim3api.usage.AnnotationBuilder;
import edu.stanford.hakan.aim3api.usage.AnnotationGetter;
import edu.stanford.hakan.aim4api.base.ImageAnnotationCollection;

/**
 * 
 * 
 * 
 * @author martin
 */
public class AIMUtil
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String aim3Namespace = EPADConfig.aim3Namespace;
	private static final String eXistUsername = EPADConfig.eXistUsername;
	private static final String eXistPassword = EPADConfig.eXistPassword;
	private static final String eXistServerUrl = EPADConfig.eXistServerUrl;
	private static final String eXistCollection = EPADConfig.eXistCollection;
	private static final String baseAnnotationDir = EPADConfig.baseAnnotationDir;
	private static final String xsdFilePath = EPADConfig.xsdFilePath;
	private static final String useV4 = EPADConfig.useV4;
	private static final String aim4Namespace = EPADConfig.aim4Namespace;
	private static final String eXistCollectionV4 = EPADConfig.eXistCollectionV4;
	private static final String xsdFileV4 = EPADConfig.xsdFileV4;
	private static final String xsdFilePathV4 = EPADConfig.xsdFilePathV4;

	/**
	 * Save the annotation to the server in the AIM database. An invalid annotation will not be saved. Save a file backup
	 * just in case.
	 * 
	 * @param ImageAnnotation
	 * @return String
	 * @throws AimException
	 * @throws edu.stanford.hakan.aim4api.base.AimException
	 */	
	public static String saveImageAnnotationToServer(ImageAnnotation aim, String projectID, int frameNumber, String jsessionID) throws AimException,
	edu.stanford.hakan.aim4api.base.AimException
	{                        
		String result = "";

		if (aim.getCodeValue() != null) { // For safety, write a backup file
			String tempXmlPath = baseAnnotationDir + "temp-" + aim.getUniqueIdentifier() + ".xml";
			String storeXmlPath = baseAnnotationDir + aim.getUniqueIdentifier() + ".xml";
			File tempFile = new File(tempXmlPath);
			File storeFile = new File(storeXmlPath);
			AnnotationBuilder.saveToFile(aim, tempXmlPath, xsdFilePath);
			log.info("Saving AIM file with ID " + aim.getUniqueIdentifier());
			result = AnnotationBuilder.getAimXMLsaveResult();

			log.info("Save AIM to file:" + result);
			if (storeFile.exists()) {
				storeFile.delete();
			}
			tempFile.renameTo(storeFile);

			if (useV4.equals("false")) {
				AnnotationBuilder.saveToServer(aim, eXistServerUrl, aim3Namespace, eXistCollection, xsdFilePath, eXistUsername,
						eXistPassword);
				result = AnnotationBuilder.getAimXMLsaveResult();
			} else {
			    String collectionName = eXistCollectionV4;
			    if (projectID != null && projectID.length() > 0)
			    	collectionName = collectionName + "/" + projectID;
				edu.stanford.hakan.aim4api.usage.AnnotationBuilder.saveToServer(aim.toAimV4(), eXistServerUrl, aim4Namespace,
						collectionName, xsdFilePathV4, eXistUsername, eXistPassword);
				result = edu.stanford.hakan.aim4api.usage.AnnotationBuilder.getAimXMLsaveResult();
			}

			log.info("Save AIM to Exist:" + result);
			log.info("CodingSchemeDesignator:" + aim.getCodingSchemeDesignator());
			
			if (aim.getCodingSchemeDesignator().equals("epad-plugin")) { // Which template has been used to fill the AIM file
				String templateName = aim.getCodeValue(); // ex: jjv-5
				log.info("Found an AIM plugin template with name " + templateName + " and AIM ID " + aim.getUniqueIdentifier());
				boolean templateHasBeenFound = false;
				String handlerName = null;
				String pluginName = null;

				List<String> list = PluginConfig.getInstance().getPluginTemplateList();
				for (int i = 0; i < list.size(); i++) {
					String templateNameFounded = list.get(i);
					if (templateNameFounded.equals(templateName)) {
						handlerName = PluginConfig.getInstance().getPluginHandlerList().get(i);
						pluginName = PluginConfig.getInstance().getPluginNameList().get(i);
						templateHasBeenFound = true;
					}
				}

				if (templateHasBeenFound) {
					log.info("Starting Plugin task for:" + pluginName);
					(new Thread(new PluginStartTask(jsessionID, pluginName, aim.getUniqueIdentifier(), frameNumber, projectID))).start();				
				}
			}
		}
		return result;
	}

	private static long getTime(String timestamp)
	{
		try
		{
			Date date = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss").parse(timestamp);
			return date.getTime();
		}
		catch (Exception x)
		{
			return 0;
		}
	}

    /**
     * @param ImageAnnotationCollection aim - AIM 4 implementation
     * @param frameNumber
     * @param jsessionID
     * @return
     * @throws AimException
     * @throws edu.stanford.hakan.aim4api.base.AimException
     */
    public static String saveImageAnnotationToServer(ImageAnnotationCollection aim, String projectID, int frameNumber, String jsessionID) throws AimException,
	    edu.stanford.hakan.aim4api.base.AimException {
    	return saveImageAnnotationToServer(aim, projectID, frameNumber, jsessionID, true);
    }
    
    public static String saveImageAnnotationToServer(ImageAnnotationCollection aim, String projectID, int frameNumber, String jsessionID, boolean invokePlugin) throws AimException,
	    edu.stanford.hakan.aim4api.base.AimException {
		String result = "";
		
		    log.info("=+=+=+=+=+=+=+=+=+=+=+=+= saveImageAnnotationToServer-1");
		if (aim.getImageAnnotations().get(0).getListTypeCode().get(0).getCode() != null) { // For safety, write a backup file
		    String tempXmlPath = baseAnnotationDir + "temp-" + aim.getUniqueIdentifier().getRoot() + ".xml";
		    String storeXmlPath = baseAnnotationDir + aim.getUniqueIdentifier().getRoot() + ".xml";
		    File tempFile = new File(tempXmlPath);
		    File storeFile = new File(storeXmlPath);
		    
		    edu.stanford.hakan.aim4api.usage.AnnotationBuilder.saveToFile(aim, tempXmlPath, xsdFilePathV4);
		    log.info("Saving AIM file with ID " + aim.getUniqueIdentifier());
		    result = AnnotationBuilder.getAimXMLsaveResult();
		
		    log.info(result);
		    if (storeFile.exists()) {
		        storeFile.delete();
		    }
		    tempFile.renameTo(storeFile);
		
		    String collectionName = eXistCollectionV4;
		    if (projectID != null && projectID.length() > 0)
		    	collectionName = collectionName + "/" + projectID;
		    edu.stanford.hakan.aim4api.usage.AnnotationBuilder.saveToServer(aim, eXistServerUrl, aim4Namespace,
		    		collectionName, xsdFilePathV4, eXistUsername, eXistPassword);
		    result = edu.stanford.hakan.aim4api.usage.AnnotationBuilder.getAimXMLsaveResult();
		
		    log.info(result);
		
		    if (aim.getImageAnnotations().get(0).getListTypeCode().get(0).getCodeSystemName().equals("epad-plugin")) { // Which template has been used to fill the AIM file
		        String templateName = aim.getImageAnnotations().get(0).getListTypeCode().get(0).getCode(); // ex: jjv-5
		        log.info("Found an AIM plugin template with name " + templateName + " and AIM ID " + aim.getUniqueIdentifier());
		        boolean templateHasBeenFound = false;
		        String handlerName = null;
		        String pluginName = null;
		
		        List<String> list = PluginConfig.getInstance().getPluginTemplateList();
		        for (int i = 0; i < list.size(); i++) {
		            String templateNameFounded = list.get(i);
		            if (templateNameFounded.equals(templateName)) {
		                handlerName = PluginConfig.getInstance().getPluginHandlerList().get(i);
		                pluginName = PluginConfig.getInstance().getPluginNameList().get(i);
		                templateHasBeenFound = true;
		            }
		        }
		
		        if (templateHasBeenFound) {
		        	// Start plugin task
					log.info("Starting Plugin task for:" + pluginName);
					(new Thread(new PluginStartTask(jsessionID, pluginName, aim.getUniqueIdentifier().getRoot(), frameNumber, projectID))).start();				
		        }
		    }
		}
		return result;
	}	
	
	public static boolean deleteAIM(String aimID)
	{
		return deleteAIM(aimID, null);
	}
	
	public static boolean deleteAIM(String aimID, String projectID)
	{
		try {
			if (useV4.equals("false"))
				AnnotationGetter.removeImageAnnotationFromServer(eXistServerUrl, aim3Namespace, eXistCollection, eXistUsername,
						eXistPassword, aimID, false);
			else
			{
			    String collectionName = eXistCollectionV4;
			    if (projectID != null && projectID.length() > 0)
			    	collectionName = collectionName + "/" + projectID;
				edu.stanford.hakan.aim4api.database.exist.ExistManager.removeImageAnnotationCollectionFromServer(
						eXistServerUrl, aim4Namespace, collectionName, eXistUsername, eXistPassword, aimID);
			}

			return true;
		} catch (Exception ex) {
			log.warning("Error deleting AIM annotation " + aimID, ex);
			return false;
		}
	}

	/**
	 * Generate an AIM file for a new DICOM Segmentation Object (DSO). This generation process is used when a new DSO is
	 * detected in dcm4chee. For the moment, we set the owner of the AIM annotation to admin.
	 * <p>
	 * This AIM file actually annotates the original image, NOT the DSO. The Referenced SOP Instance UID field in the
	 * DICOM DSO tag file identifies the image from which the segmentation object is derived from. It contains the imageID
	 * of the original image but does not contain the study or series identifiers for that image - so we need to discover
	 * them by querying ePAD.
	 */

	/**
	 * {@link PluginAIMUtil#generateAIMFileForDSO} is very similar.
	 * 
	 */
	public static void generateAIMFileForDSO(File dsoFile) throws Exception
	{
		generateAIMFileForDSO(dsoFile, "shared", null);
	}
	
	public static void generateAIMFileForDSO(File dsoFile, String username, String projectID) throws Exception
	{
		log.info("Creating DSO AIM for user " + username + " in project " + projectID);
		AttributeList dsoDICOMAttributes = PixelMedUtils.readDICOMAttributeList(dsoFile);
		String patientID = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.PatientID);
		String patientName = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.PatientName);
		String patientBirthDay = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.PatientBirthDate);
		if (patientBirthDay.trim().length() != 8) patientBirthDay = "19650212";
		String patientSex = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.PatientSex);
		if (patientSex.trim().length() != 1) patientSex = "F";
		String dsoDate = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.SeriesDate);
		if (dsoDate.trim().length() != 8) dsoDate = "20001017";
		String sopClassUID = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.SOPClassUID);
		String studyUID = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.StudyInstanceUID);
		log.info("DSO:" + dsoFile.getAbsolutePath() + " PatientID:" + patientID + " studyUID:" + studyUID);
		String seriesUID = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.SeriesInstanceUID);
		String imageUID = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.SOPInstanceUID);
		String description = Attribute.getSingleStringValueOrEmptyString(dsoDICOMAttributes, TagFromName.SeriesDescription);
		// TODO: This call to get Referenced Image does not work ???
		String[] referencedImageUID = Attribute.getStringValues(dsoDICOMAttributes, TagFromName.ReferencedSOPInstanceUID);
		SequenceAttribute referencedSeriesSequence =(SequenceAttribute)dsoDICOMAttributes.get(TagFromName.ReferencedSeriesSequence);
		if (referencedSeriesSequence != null) {
		    Iterator sitems = referencedSeriesSequence.iterator();
		    if (sitems.hasNext()) {
		        SequenceItem sitem = (SequenceItem)sitems.next();
		        if (sitem != null) {
		            AttributeList list = sitem.getAttributeList();
		            list = SequenceAttribute.getAttributeListFromWithinSequenceWithSingleItem(list,
							TagFromName.ReferencedInstanceSequence);
		            if (list.get(TagFromName.ReferencedSOPInstanceUID) != null)
		            {
		            	
		    			referencedImageUID = new String[1];
		    			referencedImageUID[0] = list.get(TagFromName.ReferencedSOPInstanceUID).getSingleStringValueOrEmptyString();
			            log.info("ReferencedSOPInstanceUID:" + referencedImageUID[0]);
		            }
 		        }
		    }
		}
		Dcm4CheeDatabaseOperations dcm4CheeDatabaseOperations = Dcm4CheeDatabase.getInstance()
				.getDcm4CheeDatabaseOperations();
		if (referencedImageUID == null || referencedImageUID.length == 0)
		{
			referencedImageUID = new String[1];
			DICOMElementList dicomElementList = Dcm4CheeQueries.getDICOMElementsFromWADO(studyUID, seriesUID, imageUID);
			for (DICOMElement dicomElement : dicomElementList.ResultSet.Result) {
				if (dicomElement.tagCode.equals(PixelMedUtils.ReferencedSOPInstanceUIDCode))
				{
					referencedImageUID[0] = dicomElement.value;
				}
			}
			if (referencedImageUID[0] == null)
				throw new Exception("DSO Referenced Image UID not found: " + seriesUID);
		}
		String referencedSeriesUID = dcm4CheeDatabaseOperations.getSeriesUIDForImage(referencedImageUID[0]);

		if (referencedSeriesUID.length() != 0) { // Found corresponding series in dcm4chee
			String referencedStudyUID = studyUID; // Will be same study as DSO

			log.info("Generating AIM file for DSO series " + seriesUID + " for patient " + patientName);
			log.info("SOP Class UID=" + sopClassUID);
			log.info("DSO Study UID=" + studyUID);
			log.info("DSO Series UID=" + seriesUID);
			log.info("DSO Image UID=" + imageUID);
			log.info("Referenced SOP Instance UID=" + referencedImageUID[0]);
			log.info("Referenced Series Instance UID=" + referencedSeriesUID);

			String name = description;
			if (name == null || name.trim().length() == 0) name = "segmentation";
			ImageAnnotation imageAnnotation = new ImageAnnotation(0, "", dsoDate.substring(0,4) + "-" + dsoDate.substring(4,6) + "-" + dsoDate.substring(6,8) + "T00:00:00", name, "SEG",
					"SEG Only", "", "", "");

			SegmentationCollection sc = new SegmentationCollection();
			sc.AddSegmentation(new Segmentation(0, imageUID, sopClassUID, referencedImageUID[0], 1));
			imageAnnotation.setSegmentationCollection(sc);

			DICOMImageReference originalDICOMImageReference = PluginAIMUtil.createDICOMImageReference(referencedStudyUID,
					referencedSeriesUID, referencedImageUID[0]);
			imageAnnotation.addImageReference(originalDICOMImageReference);
			DICOMImageReference dsoDICOMImageReference = PluginAIMUtil.createDICOMImageReference(studyUID, seriesUID,
					imageUID);
			imageAnnotation.addImageReference(dsoDICOMImageReference);

			Person person = new Person();
			person.setSex(patientSex.trim());
			if (patientBirthDay.trim().length() == 8)
				person.setBirthDate(patientBirthDay.substring(0,4) + "-" + patientBirthDay.substring(4,6) + "-" + patientBirthDay.substring(6,8) + "T00:00:00"); // TODO
			person.setId(patientID);
			person.setName(patientName);
			person.setCagridId(0);
			imageAnnotation.addPerson(person);

			// TODO Not general. See if we can generate AIM on GUI upload of DSO with correct user.
			setImageAnnotationUser(imageAnnotation, username);

			log.info("Saving AIM file for DSO " + imageUID + " in series " + seriesUID + " with ID "
					+ imageAnnotation.getUniqueIdentifier());
			try {
				String result = saveImageAnnotationToServer(imageAnnotation, projectID);
				if (result.toLowerCase().contains("success"))
				{
		    		String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
		    		if (projectID == null || projectID.trim().length() == 0)
		    			projectID = XNATQueries.getFirstProjectForStudy(adminSessionID, referencedStudyUID);
					ImageReference imageReference = new ImageReference(projectID, patientID, referencedStudyUID, referencedSeriesUID, referencedImageUID[0]);
					EpadDatabase.getInstance().getEPADDatabaseOperations().addDSOAIM(username, imageReference, seriesUID, imageAnnotation.getUniqueIdentifier());
				}
			} catch (AimException e) {
				log.warning("Exception saving AIM file for DSO image " + imageUID + " in series " + seriesUID, e);
			}
		} else {
			log.warning("DSO " + imageUID + " in series " + seriesUID + " with no corresponding DICOM image");
		}

		/*
		 * ServerEventUtil.postEvent(username, "DSOReady", imageAnnotation.getUniqueIdentifier(), aimName, patientID,
		 * patientName, "", "", "");
		 */
	}

	public static boolean uploadAIMAnnotations(HttpServletRequest httpRequest, PrintWriter responseStream,
			String annotationsUploadDirPath) throws FileUploadException, IOException, FileNotFoundException, AimException,
			edu.stanford.hakan.aim4api.base.AimException
	{ // See http://www.tutorialspoint.com/servlets/servlets-file-uploading.htm
        // Create a factory for disk-based file items
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        List<FileItem> items = upload.parseRequest(httpRequest);
        // Process the uploaded items
        Iterator<FileItem> fileItemIterator = items.iterator();
		String username = httpRequest.getParameter("username");
		int fileCount = 0;
		boolean saveError = false;
		String projectID = httpRequest.getParameter("projectID");
		String patientID = httpRequest.getParameter("patientID");
		String studyID = httpRequest.getParameter("studyUID");
		String seriesID = httpRequest.getParameter("seriesUID");
		String imageID = httpRequest.getParameter("imageUID");
		String frameNo = "0";
		while (fileItemIterator.hasNext()) {
			FileItem fileItem = fileItemIterator.next();
		    if (fileItem.isFormField()) {
		    	if (fileItem.getFieldName().equals("projectID"))
		    	{
		    		projectID = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("patientID"))
		    	{
		    		patientID = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("studyID"))
		    	{
		    		studyID = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("seriesID"))
		    	{
		    		seriesID = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("imageID"))
		    	{
		    		imageID = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("frameNo"))
		    	{
		    		frameNo = fileItem.getString();
		    	}
		    	else if (fileItem.getFieldName().equals("username"))
		    	{
		    		username = fileItem.getString();
		    	}
		    } else {
				fileCount++;		    	
				log.debug("Uploading annotation number " + fileCount);
				//FileItemStream fileItemStream = fileItemIterator.next();
				String name = fileItem.getFieldName();
				//InputStream inputStream = fileItemStream.openStream();
				String tempXMLFileName = "temp-" + System.currentTimeMillis() + ".xml";
				File aimFile = new File(annotationsUploadDirPath + tempXMLFileName);
                // write the file
		        try {
					fileItem.write(aimFile);
				} catch (Exception e) {
					e.printStackTrace();
					log.warning("Error receiving AIM file:" + e);
					responseStream.print("error reading (" + fileCount + "): " + name);
					continue;
				}
				responseStream.print("added (" + fileCount + "): " + name);
				if (useV4.equals("false"))
				{
					ImageAnnotation imageAnnotation = AIMUtil.getImageAnnotationFromFile(aimFile, xsdFilePath);
					if (imageAnnotation != null) {
						if (patientID == null) patientID = imageAnnotation.getListPerson().get(0).getId();
						if (studyID == null)
						{
							Aim aim = new Aim(imageAnnotation);
							imageID = aim.getFirstImageID();
							seriesID = aim.getSeriesID(imageID);
							studyID = aim.getStudyID(seriesID);
						}
						String jsessionID = XNATSessionOperations.getJSessionIDFromRequest(httpRequest);
						log.info("Saving AIM file with ID " + imageAnnotation.getUniqueIdentifier() + " username:" + username);
						String result = AIMUtil.saveImageAnnotationToServer(imageAnnotation, projectID, 0, jsessionID);
						if (result.toLowerCase().contains("success") && projectID != null && username != null)
						{
							EpadOperations epadOperations = DefaultEpadOperations.getInstance();
							FrameReference frameReference = new FrameReference(projectID, patientID, studyID, seriesID, imageID, getInt(frameNo));
							epadOperations.createFrameAIM(username, frameReference, imageAnnotation.getUniqueIdentifier(), null, jsessionID);
						}
						responseStream.println("-- Add to AIM server: " + imageAnnotation.getUniqueIdentifier() + "<br>");
					} else {
						responseStream.println("-- Failed ! not added to AIM server<br>");
						saveError = true;
					}
				}
				else
				{
		            log.info("=+=+=+=+=+=+=+=+=+=+=+=+= uploadAIMAnnotations-2");
		            log.info("=+=+=+=+=+=+=+=+=+=+=+=+= xsdFilePathV4 : " + xsdFilePathV4);
		            ImageAnnotationCollection imageAnnotation = AIMUtil.getImageAnnotationFromFileV4(aimFile, xsdFilePathV4);
		            log.info("=+=+=+=+=+=+=+=+=+=+=+=+= uploadAIMAnnotations-3");
		            if (imageAnnotation != null) {
		                String jsessionID = XNATSessionOperations.getJSessionIDFromRequest(httpRequest);

		            log.info("=+=+=+=+=+=+=+=+=+=+=+=+= uploadAIMAnnotations-4");
		                AIMUtil.saveImageAnnotationToServer(imageAnnotation, projectID, getInt(frameNo), jsessionID);
		                responseStream.println("-- Add to AIM server: " + imageAnnotation.getUniqueIdentifier().getRoot() + "<br>");
		            } else {
		                responseStream.println("-- Failed ! not added to AIM server<br>");
		                saveError = true;
		            }
				}
				if (aimFile.exists()) aimFile.delete();				
				projectID = null;
				patientID = null;
				studyID = null;
				seriesID = null;
				imageID = null;
				frameNo = "0";
		    }
		}
		return saveError;
	}

	/**
	 * @param aimFile
	 * @param sessionId
	 * @param username
	 * @return true if error in AIM save
	 * @throws AimException
	 * @throws edu.stanford.hakan.aim4api.base.AimException
	 */
	public static boolean saveAIMAnnotation(File aimFile, String projectID, String sessionId, String username) throws AimException,
	edu.stanford.hakan.aim4api.base.AimException
	{
		return saveAIMAnnotation(aimFile, projectID, 0, sessionId, username);
	}
	
	public static boolean saveAIMAnnotation(File aimFile, String projectID, int frameNumber, String sessionId, String username) throws AimException,
	edu.stanford.hakan.aim4api.base.AimException
	{
		if (aimFile == null)
			return true;
		if (useV4.equals("false")) {
			ImageAnnotation imageAnnotation = AIMUtil.getImageAnnotationFromFile(aimFile, xsdFilePath);
			if (imageAnnotation != null) {
				String patientID = imageAnnotation.getListPerson().get(0).getId();
				Aim aim = new Aim(imageAnnotation);
				String imageID = aim.getFirstImageID();
				String seriesID = aim.getSeriesID(imageID);
				String studyID = aim.getStudyID(seriesID);
				log.info("Saving AIM file with ID " + imageAnnotation.getUniqueIdentifier() + " username:" + username);
				String result = AIMUtil.saveImageAnnotationToServer(imageAnnotation, projectID, frameNumber, sessionId);
				log.info("Save annotation:" + result);
				if (result.toLowerCase().contains("success") && projectID != null && username != null)
				{
					EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
					FrameReference frameReference = new FrameReference(projectID, patientID, studyID, seriesID, imageID, new Integer(frameNumber));
					epadDatabaseOperations.addAIM(username, frameReference, imageAnnotation.getUniqueIdentifier());
				}
				return false;
			}
		} else {
            ImageAnnotationCollection imageAnnotation = AIMUtil.getImageAnnotationFromFileV4(aimFile, xsdFilePathV4);
            if (imageAnnotation != null) {
				EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
				EPADAIM ea = epadDatabaseOperations.getAIM(imageAnnotation.getUniqueIdentifier().getRoot());
				if (ea != null && !ea.projectID.equals(projectID))
					projectID = ea.projectID; 		// TODO: Do we change AIM project if it is in unassigned? 
				Aim4 aim = new Aim4(imageAnnotation);
				String patientID = aim.getPatientID();
				String imageID = aim.getFirstImageID();
				String seriesID = aim.getSeriesID(imageID);
				String studyID = aim.getStudyID(seriesID);
				log.info("Saving AIM file with ID " + imageAnnotation.getUniqueIdentifier() + " projectID:" + projectID + " username:" + username);
				String result = AIMUtil.saveImageAnnotationToServer(imageAnnotation, projectID, frameNumber, sessionId);
				log.info("Save annotation:" + result);
				if (result.toLowerCase().contains("success") && projectID != null && username != null)
				{
					FrameReference frameReference = new FrameReference(projectID, patientID, studyID, seriesID, imageID, new Integer(frameNumber));
					epadDatabaseOperations.addAIM(username, frameReference, imageAnnotation.getUniqueIdentifier().getRoot());
				}
				return false;
           } 
			
		}
		return true;
	}

	public static AIMSearchType getAIMSearchType(HttpServletRequest httpRequest)
	{
		for (AIMSearchType aimSearchType : AIMSearchType.values()) {
			if (httpRequest.getParameter(aimSearchType.getName()) != null)
				return aimSearchType;
		}
		log.warning("No valid AIM search type parameter found");
		return null;
	}

	public static void queryAIMImageAnnotations(PrintWriter responseStream, AIMSearchType aimSearchType,
			Map<String,String> searchValueByProject, String user, int index, int count) throws ParserConfigurationException, AimException, edu.stanford.hakan.aim4api.base.AimException
	{
		if (useV4.equals("false")) {
			queryAIMImageAnnotations(responseStream, aimSearchType, searchValueByProject.values().iterator().next(), user, index, count);
		} else {
			for(String projectID: searchValueByProject.keySet())
			{
				log.info("ProjectID:" + projectID + " type:" + aimSearchType + " value:" + searchValueByProject.get(projectID));
				queryAIMImageAnnotationsV4(responseStream, projectID, aimSearchType, searchValueByProject.get(projectID), user);
			}
		}
	}

	public static void queryAIMImageAnnotations(PrintWriter responseStream, AIMSearchType aimSearchType,
			Map<String,String> searchValueByProject, String user) throws ParserConfigurationException, AimException, edu.stanford.hakan.aim4api.base.AimException
	{
		if (useV4.equals("false")) {
			queryAIMImageAnnotations(responseStream, aimSearchType, searchValueByProject.values().iterator().next(), user, 1, 5000);
		} else {
			for(String projectID: searchValueByProject.keySet())
			{
				log.info("ProjectID:" + projectID + " type:" + aimSearchType + " value:" + searchValueByProject.get(projectID));
				queryAIMImageAnnotationsV4(responseStream, projectID, aimSearchType, searchValueByProject.get(projectID), user);
			}
		}
	}

	public static void queryAIMImageAnnotations(PrintWriter responseStream, String projectID, AIMSearchType aimSearchType,
			String searchValue, String user) throws ParserConfigurationException, AimException, edu.stanford.hakan.aim4api.base.AimException
	{
		if (useV4.equals("false")) {
			queryAIMImageAnnotations(responseStream, aimSearchType, searchValue, user, 1, 5000);
		} else {
			queryAIMImageAnnotationsV4(responseStream, projectID, aimSearchType, searchValue, user);
		}
	}

	public static void queryAIMImageAnnotations(PrintWriter responseStream, String projectID, AIMSearchType aimSearchType,
			String searchValue, String user, int startIndex, int count) throws ParserConfigurationException, AimException, edu.stanford.hakan.aim4api.base.AimException
	{
		if (useV4.equals("false")) {
			queryAIMImageAnnotations(responseStream, aimSearchType, searchValue, user, startIndex, count);
		} else {
			queryAIMImageAnnotationsV4(responseStream, projectID, aimSearchType, searchValue, user);
		}
	}

	public static void queryAIMImageAnnotations(PrintWriter responseStream, AIMSearchType aimSearchType,
			String searchValue, String user, int index, int count) throws ParserConfigurationException, AimException
		{
		
		List<ImageAnnotation> aims = AIMQueries.getAIMImageAnnotations(null, aimSearchType, searchValue, user, index, count);

		log.info("" + aims.size() + " AIM file(s) found for user " + user);

		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
		Document doc = docBuilder.newDocument();
		Element root = doc.createElement("imageAnnotations");
		doc.appendChild(root);

		long starttime = System.currentTimeMillis();
		for (ImageAnnotation aim : aims) {
			//if (aim.getListUser().size() > 0)
			//	log.info("Aim name:" + aim.getName() + " Owner:" + aim.getListUser().get(0).getLoginName() + " Series:" + aim.getFirstSeriesID());
			//else
			//	log.info("Aim name:" + aim.getName() + " Owner:unknown, Series:" + aim.getFirstSeriesID());
			Node node = aim.getXMLNode(docBuilder.newDocument());
			Node copyNode = doc.importNode(node, true);
			Element res = (Element)copyNode; // Copy the node
			res.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			res.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			res.setAttribute("xsi:schemaLocation",
					"gme://caCORE.caCORE/3.2/edu.northwestern.radiology.AIM AIM_v3_rv11_XML.xsd");
			Node n = renameNodeNS(res, "ImageAnnotation","gme://caCORE.caCORE/3.2/edu.northwestern.radiology.AIM");
			root.appendChild(n); // Adding to the root
		}
		long xmlbldtime = System.currentTimeMillis();
		log.info("Time taken for convert annotations to xml:" + (xmlbldtime-starttime) + " msecs");
		String queryResults = XmlDocumentToString(doc, "gme://caCORE.caCORE/3.2/edu.northwestern.radiology.AIM");
		long xmlstrtime = System.currentTimeMillis();
		log.info("Time taken for convert xml to string:" + (xmlstrtime-xmlbldtime) + " msecs");
		responseStream.print(queryResults);
		log.info("" + aims.size() + " annotations returned to client");
		long resptime = System.currentTimeMillis();
		log.info("Time taken for write http response:" + (resptime-xmlstrtime) + " msecs, length:" + queryResults.length());
	}

	public static EPADAIMList queryAIMImageAnnotationSummaries(EPADAIMList aims, String user, int index, int count, String sessionID) throws ParserConfigurationException, AimException
	{
		Map<String, String> projectAimIDs = getUIDCsvList(sessionID, aims, user);
		List<ImageAnnotation> annotations = new ArrayList<ImageAnnotation>();
		for (String projectId: projectAimIDs.keySet())
		{
			annotations.addAll(AIMQueries.getAIMImageAnnotations(projectId, AIMSearchType.ANNOTATION_UID, projectAimIDs.get(projectId), user, index, count));
		}
		log.info("" + annotations.size() + " AIM file(s) found for user " + user);

		Map<String, EPADAIM> aimMAP = new HashMap<String, EPADAIM>();
		EPADAIMResultSet rs = aims.ResultSet;
		for (EPADAIM aim: rs.Result)
		{
			aimMAP.put(aim.aimID, aim);
		}
		aims = new EPADAIMList();
		long starttime = System.currentTimeMillis();
		for (ImageAnnotation aim : annotations) {
			Aim a = new Aim(aim);
			EPADAIM ea = aimMAP.get(aim.getUniqueIdentifier());
			ea.name = aim.getName();
			ea.template = aim.getCodeMeaning();
			ea.date = aim.getDateTime();
			ea.comment = a.getComment();
			if (a.getFirstStudyDate() != null)
				ea.studyDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(a.getFirstStudyDate());
			ea.patientName = a.getPatientName();
			aims.addAIM(ea);
		}
		long parsetime = System.currentTimeMillis();
		log.info("Time taken to parse annotations:" + (parsetime-starttime) + " msecs");
		log.info("" + annotations.size() + " annotations returned to client");
		return aims;
	}

	public static void queryAIMImageAnnotationsV4(PrintWriter responseStream, String projectID, AIMSearchType aimSearchType,
			String searchValue, String user) throws ParserConfigurationException, edu.stanford.hakan.aim4api.base.AimException
	{
		List<ImageAnnotationCollection> aims = AIMQueries.getAIMImageAnnotationsV4(projectID, aimSearchType, searchValue, user);
		log.info("" + aims.size() + " AIM4 file(s) found for user " + user);

		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
		Document doc = docBuilder.newDocument();
		Element root = doc.createElement("imageAnnotations");
		doc.appendChild(root);

		for (ImageAnnotationCollection aim : aims) {
			Node node = aim.getXMLNode(docBuilder.newDocument());
			Node copyNode = doc.importNode(node, true);
			Element res = (Element)copyNode; // Copy the node
			res.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			res.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			res.setAttribute("xsi:schemaLocation",
					"gme://caCORE.caCORE/4.4/edu.northwestern.radiology.AIM AIM_v4_rv44_XML.xsd");
			res.setAttribute("xmlns", "gme://caCORE.caCORE/4.4/edu.northwestern.radiology.AIM");
			Node n = renameNodeNS(res, "ImageAnnotationCollection","gme://caCORE.caCORE/4.4/edu.northwestern.radiology.AIM");
			root.appendChild(n); // Adding to the root
		}
		String queryResults = XmlDocumentToString(doc,"gme://caCORE.caCORE/4.4/edu.northwestern.radiology.AIM");
		
		//log.info(queryResults);
		responseStream.print(queryResults);
	}

	public static EPADAIMList queryAIMImageAnnotationSummariesV4(EPADAIMList aims, String user, int index, int count, String sessionID) throws ParserConfigurationException, AimException
	{
		List<ImageAnnotationCollection> annotations = new ArrayList<ImageAnnotationCollection>();
		Map<String, String> projectAimIDs = getUIDCsvList(sessionID, aims, user);
		for (String projectID: projectAimIDs.keySet())
		{
			annotations.addAll(AIMQueries.getAIMImageAnnotationsV4(projectID, AIMSearchType.ANNOTATION_UID, projectAimIDs.get(projectID), user));
		}
		log.info("" + annotations.size() + " AIM4 file(s) found for user " + user);

		Map<String, EPADAIM> aimMAP = new HashMap<String, EPADAIM>();
		EPADAIMResultSet rs = aims.ResultSet;
		for (EPADAIM aim: rs.Result)
		{
			aimMAP.put(aim.aimID, aim);
		}
		aims = new EPADAIMList();
		long starttime = System.currentTimeMillis();
		for (ImageAnnotationCollection aim : annotations) {
			Aim4 a = new Aim4(aim);
			EPADAIM ea = aimMAP.get(aim.getUniqueIdentifier());
			ea.name = aim.getImageAnnotations().get(0).getName().toString();
			ea.template = aim.getImageAnnotations().get(0).getListTypeCode().get(0).getCode();
			ea.date = aim.getDateTime();
			ea.comment = a.getComment();
			if (a.getFirstStudyDate() != null)
				ea.studyDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(a.getFirstStudyDate());
			ea.patientName = a.getPatientName();
			aims.addAIM(ea);
		}
		log.info("" + annotations.size() + " annotations returned to client");
		return aims;
	}

	private static String XmlDocumentToString(Document document, String xmlns)
	{ // Create an XML document from a String
		new XmlNamespaceTranslator().addTranslation(null, xmlns)
				.addTranslation("", xmlns).translateNamespaces(document);

		TransformerFactory transfac = TransformerFactory.newInstance();
		Transformer trans = null;

		try {
			trans = transfac.newTransformer();
		} catch (TransformerConfigurationException e) {
			log.warning("Error transforming XML document", e);
		}

		trans.setOutputProperty(OutputKeys.INDENT, "yes");

		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		DOMSource source = new DOMSource(document);

		try {
			trans.transform(source, result);
		} catch (TransformerException e) {
			log.warning("Error transforming XML document", e);
		}
		return sw.toString();
	}

	// Rename namespace of the nodes
	private static Node renameNodeNS(Node node, String newName, String xmlns)
	{
		Element newNode = node.getOwnerDocument().createElementNS(xmlns,
				newName);
		NamedNodeMap map = node.getAttributes();
		for (int i = 0; i < map.getLength(); i++) {
			newNode.setAttribute(map.item(i).getNodeName(), map.item(i).getNodeValue());
		}

		NodeList tempList = node.getChildNodes();
		for (int i = 0; i < tempList.getLength(); i++) {
			newNode.appendChild(tempList.item(i).cloneNode(true));
		}
		return newNode;
	}

	public static ImageAnnotation getImageAnnotationFromFile(File file, String xsdFilePath) throws AimException,
			edu.stanford.hakan.aim4api.base.AimException
	{
		ImageAnnotation ia = AnnotationGetter.getImageAnnotationFromFile(file.getAbsolutePath(), xsdFilePath);
		log.info("Annotation:" + file.getAbsolutePath() + " PatientId:" + ia.getListPerson().get(0).getId());
		return ia;
	}

    public static ImageAnnotationCollection getImageAnnotationFromFileV4(File file, String xsdFilePath) throws AimException,
            edu.stanford.hakan.aim4api.base.AimException {
        return edu.stanford.hakan.aim4api.usage.AnnotationGetter.getImageAnnotationCollectionFromFile(file.getAbsolutePath(), xsdFilePath);
    }

	private static void setImageAnnotationUser(ImageAnnotation imageAnnotation, String username)
	{
		List<User> userList = new ArrayList<User>();
		User user = new User();
		user.setLoginName(username);
		user.setName(username);
		user.setCagridId(0);
		userList.add(user);
		imageAnnotation.setListUser(userList);
	}

	private static String saveImageAnnotationToServer(ImageAnnotation aim, String projectID) throws AimException,
			edu.stanford.hakan.aim4api.base.AimException
	{
		String result = "";

		if (aim.getCodeValue() != null) { // First, write a backup file
			String tempXmlPath = baseAnnotationDir + "temp-" + aim.getUniqueIdentifier() + ".xml";
			String storeXmlPath = baseAnnotationDir + aim.getUniqueIdentifier() + ".xml";
			File tempFile = new File(tempXmlPath);
			File storeFile = new File(storeXmlPath);

			if (useV4.equals("false")) {
				AnnotationBuilder.saveToFile(aim, tempXmlPath, xsdFilePath);
				result = AnnotationBuilder.getAimXMLsaveResult();
			} else {
				edu.stanford.hakan.aim4api.usage.AnnotationBuilder.saveToFile(aim.toAimV4(), tempXmlPath, xsdFilePathV4);
				result = edu.stanford.hakan.aim4api.usage.AnnotationBuilder.getAimXMLsaveResult();
			}

			log.info("Save AIM file:" + result);
			if (storeFile.exists()) {
				storeFile.delete();
			}
			tempFile.renameTo(storeFile);
			if (useV4.equals("false")) {
				AnnotationBuilder.saveToServer(aim, eXistServerUrl, aim3Namespace, eXistCollection, xsdFilePath, eXistUsername,
						eXistPassword);
				result = AnnotationBuilder.getAimXMLsaveResult();
			} else {
			    String collectionName = eXistCollectionV4;
			    if (projectID != null && projectID.length() > 0)
			    	collectionName = collectionName + "/" + projectID;
				edu.stanford.hakan.aim4api.usage.AnnotationBuilder.saveToServer(aim.toAimV4(), eXistServerUrl, aim4Namespace,
						collectionName, xsdFilePathV4, eXistUsername, eXistPassword);
				result = edu.stanford.hakan.aim4api.usage.AnnotationBuilder.getAimXMLsaveResult();
			}
			log.info("Save AIM to Exist:" + result);
		}
		return result;
	}
	
	public static Map<String, String> getUIDCsvList(String sessionID, EPADAIMList aimlist, String username)
	{
		long starttime = System.currentTimeMillis();
		Set<String> projectIDs = aimlist.getProjectIds();
		Map<String,String> projectAIMs = new HashMap<String, String>();
		String defProjectID = EPADConfig.xnatUploadProjectID;
		for (String projectID: projectIDs)
		{
			try
			{
				String csv = "";
				boolean isCollaborator = XNATQueries.isCollaborator(sessionID, username, projectID);
				defProjectID = projectID;
				log.info("User:" + username + " projectID:" + projectID + " isCollaborator:" + isCollaborator);
				Set<EPADAIM> aims = aimlist.getAIMsForProject(projectID);
				for (EPADAIM aim: aims)
				{
					if (!isCollaborator || aim.userName.equals(username) || aim.userName.equals("shared"))
						csv = csv + "," +  aim.aimID;
				}
				if (csv.length() != 0)
				{
					projectAIMs.put(projectID, csv.substring(1));
				}
			}
			catch (Exception x) {
				log.warning("Error checking AIM permission:", x);
			}
		}
		long endtime = System.currentTimeMillis();
		log.info("Time taken for checking AIM permissions:" + (endtime-starttime) + " msecs, username:" + username);
		
		if (projectAIMs.keySet().isEmpty())
			projectAIMs.put(defProjectID, "");
		return projectAIMs;
	}
	

	public static int convertAllAim3() throws Exception {
		List<EPADAIM> epadaims = EpadDatabase.getInstance().getEPADDatabaseOperations().getAIMs(new ProjectReference(null));
		String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
		int count = 0;
		for (EPADAIM epadaim: epadaims)
		{
			log.info("Converting AIM3:" + epadaim.aimID + " in project " + epadaim.projectID);
			try {
				List<ImageAnnotation> aims = AIMQueries.getAIMImageAnnotations(epadaim.projectID, AIMSearchType.ANNOTATION_UID, epadaim.aimID, "admin", 1, 50000, true);
				if (aims.size() > 0)
				{
					log.info("Saving AIM4:" + epadaim.aimID + " in project " + epadaim.projectID);
					String result = AIMUtil.saveImageAnnotationToServer(aims.get(0).toAimV4(), epadaim.projectID, 0, adminSessionID, false);
					log.info("Save AIM4 results:" + result);
					count++;
				}
				else
					log.info("AIM4:" + epadaim.aimID + " in project " + epadaim.projectID + " not found");
					
			}
			catch (Exception x) {
				log.warning("Error converting aim3:" + epadaim.aimID, x);
			}
		}
		return count;
	}
	
	private static int getInt(String value)
	{
		try {
			return new Integer(value.trim()).intValue();
		} catch (Exception x) {
			return 0;
		}
	}

}
