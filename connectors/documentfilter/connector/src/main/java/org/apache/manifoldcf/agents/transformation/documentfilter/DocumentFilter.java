/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.agents.transformation.documentfilter;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import org.apache.manifoldcf.agents.system.ManifoldCF;
import org.apache.manifoldcf.agents.system.Logging;

import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.*;

public class DocumentFilter extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector {

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  
  private static final String EDIT_SPECIFICATION_CONTENTS_HTML = "editSpecification_Contents.html";
  
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";
  
  /** Constructor.
   */
  public DocumentFilter(){
  }
  
  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
  * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
  * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
  * is used to describe the version of the actual document.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param os is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  @Override
  public VersionContext getPipelineDescription(Specification os)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(os);
    return new VersionContext(sp.toPackedString(),params,os);
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param outputDescription is the document's output version.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext outputDescription, String mimeType, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(outputDescription.getVersionString());
    if (sp.checkMimeType(mimeType))
      return super.checkMimeTypeIndexable(outputDescription, mimeType, activities);
    else
      return false;
  }

  @Override
  public boolean checkLengthIndexable(VersionContext outputDescription, long length, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(outputDescription.getVersionString());
    if (sp.checkLengthIndexable(length))
      return super.checkLengthIndexable(outputDescription, length, activities);
    else
      return false;
  }

  @Override
  public boolean checkURLIndexable(VersionContext outputDescription, String url, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(outputDescription.getVersionString());
    if (sp.checkURLIndexable(url))
      return super.checkURLIndexable(outputDescription, url, activities);
    else
      return false;
  }
  
  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    return activities.sendDocument(documentURI, document);
  }
  
  protected static void fillInContentsSpecificationMap(Map<String,Object> paramMap, Specification os)
  {
    String minFileSize = DocumentFilterConfig.MINLENGTH_DEFAULT;
    String maxFileSize = DocumentFilterConfig.MAXLENGTH_DEFAULT;
    String allowedMimeTypes = DocumentFilterConfig.MIMETYPES_DEFAULT;
    String allowedFileExtensions = DocumentFilterConfig.EXTENSIONS_DEFAULT;
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(DocumentFilterConfig.NODE_MAXLENGTH))
        maxFileSize = sn.getAttributeValue(DocumentFilterConfig.ATTRIBUTE_VALUE);
      else if (sn.getType().equals(DocumentFilterConfig.NODE_MINLENGTH))
        minFileSize = sn.getAttributeValue(DocumentFilterConfig.ATTRIBUTE_VALUE);
      else if (sn.getType().equals(DocumentFilterConfig.NODE_MIMETYPES))
        allowedMimeTypes = sn.getValue();
      else if (sn.getType().equals(DocumentFilterConfig.NODE_EXTENSIONS))
        allowedFileExtensions = sn.getValue();
    }
    paramMap.put("MINFILESIZE",minFileSize);
    paramMap.put("MAXFILESIZE",maxFileSize);
    paramMap.put("MIMETYPES",allowedMimeTypes);
    paramMap.put("EXTENSIONS",allowedFileExtensions);
  }
  
  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  @Override
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecification";
  }

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecificationForSave";
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a pipeline connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this connection.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "DocumentFilter.ContentsTabName"));

    // Fill in the specification header map, using data from all tabs.
    fillInContentsSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_JS,paramMap);
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a pipeline connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM",Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInContentsSpecificationMap(paramMap, os);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_CONTENTS_HTML,paramMap);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String x;

    x = variableContext.getParameter(seqPrefix+"minfilesize");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(DocumentFilterConfig.NODE_MINLENGTH))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(DocumentFilterConfig.NODE_MINLENGTH);
      sn.setAttribute(DocumentFilterConfig.ATTRIBUTE_VALUE,x);
      os.addChild(os.getChildCount(),sn);
    }

    x = variableContext.getParameter(seqPrefix+"maxfilesize");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(DocumentFilterConfig.NODE_MAXLENGTH))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(DocumentFilterConfig.NODE_MAXLENGTH);
      sn.setAttribute(DocumentFilterConfig.ATTRIBUTE_VALUE,x);
      os.addChild(os.getChildCount(),sn);
    }

    x = variableContext.getParameter(seqPrefix+"mimetypes");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(DocumentFilterConfig.NODE_MIMETYPES))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(DocumentFilterConfig.NODE_MIMETYPES);
      sn.setValue(x);
      os.addChild(os.getChildCount(),sn);
    }

    x = variableContext.getParameter(seqPrefix+"extensions");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(DocumentFilterConfig.NODE_EXTENSIONS))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(DocumentFilterConfig.NODE_EXTENSIONS);
      sn.setValue(x);
      os.addChild(os.getChildCount(),sn);
    }
    
    return null;
  }
  

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the pipeline specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current pipeline specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInContentsSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPECIFICATION_HTML,paramMap);
    
  }
  
  protected static void fillSet(Set<String> set, String input) {
    try
    {
      StringReader sr = new StringReader(input);
      BufferedReader br = new BufferedReader(sr);
      String line = null;
      while ((line = br.readLine()) != null)
      {
        line = line.trim();
        if (line.length() > 0)
          set.add(line.toLowerCase(Locale.ROOT));
      }
    }
    catch (IOException e)
    {
      // Should never happen
      throw new RuntimeException("IO exception reading strings: "+e.getMessage(),e);
    }
  }
  
  protected static class SpecPacker {
    
    private final Set<String> extensions = new HashSet<String>();
    private final Set<String> mimeTypes = new HashSet<String>();
    private final Long minLength;
    private final Long lengthCutoff;
    
    public SpecPacker(Specification os) {
      Long minLength = null;
      Long lengthCutoff = null;
      String extensions = null;
      String mimeTypes = null;
      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);
        
        if (sn.getType().equals(DocumentFilterConfig.NODE_MIMETYPES)) {
          mimeTypes = sn.getValue();
        } else if (sn.getType().equals(DocumentFilterConfig.NODE_EXTENSIONS)) {
          extensions = sn.getValue();
        } else if (sn.getType().equals(DocumentFilterConfig.NODE_MAXLENGTH)) {
          String value = sn.getAttributeValue(DocumentFilterConfig.ATTRIBUTE_VALUE);
          lengthCutoff = new Long(value);
        } else if (sn.getType().equals(DocumentFilterConfig.NODE_MINLENGTH)) {
          String value = sn.getAttributeValue(DocumentFilterConfig.ATTRIBUTE_VALUE);
          minLength = new Long(value);
        }
      }
      this.minLength = minLength;
      this.lengthCutoff = lengthCutoff;
      fillSet(this.extensions, extensions);
      fillSet(this.mimeTypes, mimeTypes);
    }
    
    public SpecPacker(String packedString) {
      
      int index = 0;
      
      // Max length
      if (packedString.length() > index) {
        if (packedString.charAt(index++) == '+') {
          final StringBuilder sb = new StringBuilder();
          index = unpack(sb,packedString,index,'+');
          this.lengthCutoff = new Long(sb.toString());
        } else
          this.lengthCutoff = null;
      } else
        this.lengthCutoff = null;
      
      // Mime types
      final List<String> mimeBuffer = new ArrayList<String>();
      index = unpackList(mimeBuffer,packedString,index,'+');
      for (String mimeType : mimeBuffer) {
        this.mimeTypes.add(mimeType);
      }
      
      // Extensions
      final List<String> extensionsBuffer = new ArrayList<String>();
      index = unpackList(extensionsBuffer,packedString,index,'+');
      for (String extension : extensionsBuffer) {
        this.extensions.add(extension);
      }
      
      // Min length
      if (packedString.length() > index) {
        if (packedString.charAt(index++) == '+') {
          final StringBuilder sb = new StringBuilder();
          index = unpack(sb,packedString,index,'+');
          this.minLength = new Long(sb.toString());
        } else
          this.minLength = null;
      } else
        this.minLength = null;

    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      int i;
      
      // Max length
      if (lengthCutoff == null)
        sb.append('-');
      else {
        sb.append('+');
        pack(sb,lengthCutoff.toString(),'+');
      }
      
      // Mime types
      String[] mimeTypes = new String[this.mimeTypes.size()];
      i = 0;
      for (String mimeType : this.mimeTypes) {
        mimeTypes[i++] = mimeType;
      }
      java.util.Arrays.sort(mimeTypes);
      packList(sb,mimeTypes,'+');
      
      // Extensions
      String[] extensions = new String[this.extensions.size()];
      i = 0;
      for (String extension : this.extensions) {
        extensions[i++] = extension;
      }
      java.util.Arrays.sort(extensions);
      packList(sb,extensions,'+');

      // Min length
      if (minLength == null)
        sb.append('-');
      else {
        sb.append('+');
        pack(sb,minLength.toString(),'+');
      }
      
      return sb.toString();
    }
    
    public boolean checkLengthIndexable(long length) {
      if (minLength != null && length < minLength.longValue())
        return false;
      if (lengthCutoff != null && length > lengthCutoff.longValue())
        return false;
      return true;
    }
    
    public boolean checkMimeType(String mimeType) {
      if (mimeType == null)
        mimeType = "application/unknown";
      return mimeTypes.contains(mimeType.toLowerCase(Locale.ROOT));
    }
    
    public boolean checkURLIndexable(String url) {
      String extension = FilenameUtils.getExtension(url);
      if (extension == null || extension.length() == 0)
        extension = ".";
      return extensions.contains(extension.toLowerCase(Locale.ROOT));
    }
    
  }
  
}
