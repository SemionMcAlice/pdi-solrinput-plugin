/*! ******************************************************************************
*
* Pentaho Data Integration
*
* Copyright (C) 2002-2013 by Pentaho : http://www.pentaho.com
*
*******************************************************************************
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
******************************************************************************/

package org.pentaho.di.trans.steps.solrinput;

import java.util.ArrayList;
import java.util.List;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.trans.steps.solrinput.SolrInputData;
import org.pentaho.di.trans.steps.solrinput.SolrInputMeta;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;

/**
 * This class is part of the demo step plug-in implementation.
 * It demonstrates the basics of developing a plug-in step for PDI. 
 * 
 * The demo step adds a new string field to the row stream and sets its
 * value to "Hello World!". The user may select the name of the new field.
 *   
 * This class is the implementation of StepInterface.
 * Classes implementing this interface need to:
 * 
 * - initialize the step
 * - execute the row processing logic
 * - dispose of the step 
 * 
 * Please do not create any local fields in a StepInterface class. Store any
 * information related to the processing logic in the supplied step data interface
 * instead.  
 * 
 */

public class SolrInput extends BaseStep implements StepInterface {
	
	private static Class<?> PKG = SolrInputMeta.class; 
    private SolrInputMeta meta;
    private SolrInputData data;

	/**
	 * The constructor should simply pass on its arguments to the parent class.
	 * 
	 * @param s 				step description
	 * @param stepDataInterface	step data class
	 * @param c					step copy
	 * @param t					transformation description
	 * @param dis				transformation executing
	 */
	public SolrInput(StepMeta s, StepDataInterface stepDataInterface, 
			int c, TransMeta t, Trans dis) {
		super(s, stepDataInterface, c, t, dis);
	}
	
	/**
	 * Once the transformation starts executing, the processRow() method is called repeatedly
	 * by PDI for as long as it returns true. To indicate that a step has finished processing rows
	 * this method must call setOutputDone() and return false;
	 * 
	 * Steps which process incoming rows typically call getRow() to read a single row from the
	 * input stream, change or add row content, call putRow() to pass the changed row on 
	 * and return true. If getRow() returns null, no more rows are expected to come in, 
	 * and the processRow() implementation calls setOutputDone() and returns false to
	 * indicate that it is done too.
	 * 
	 * Steps which generate rows typically construct a new row Object[] using a call to
	 * RowDataUtil.allocateRowData(numberOfFields), add row content, and call putRow() to
	 * pass the new row on. Above process may happen in a loop to generate multiple rows,
	 * at the end of which processRow() would call setOutputDone() and return false;
	 * 
	 * @param smi the step meta interface containing the step settings
	 * @param sdi the step data interface that should be used to store
	 * 
	 * @return true to indicate that the function should be called again, false if the step is done
	 */
	  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {
		  
		    if ( first ) {
		      first = false;
		      // Create the output row meta-data
		      data.outputRowMeta = new RowMeta();
		      meta.getFields( data.outputRowMeta, getStepname(), null, null, this, repository, metaStore );
		      // For String to <type> conversions, we allocate a conversion meta data row as well...
		      data.convertRowMeta = data.outputRowMeta.cloneToType( ValueMetaInterface.TYPE_STRING );
		      
		      // get real values
		      boolean tryCursor = true;
		      Integer startRecord = 0;
		      Integer chunkRowSize = 20;
		      String realURL = meta.getURL();
		      String realQ = meta.getQ();
		      String realSort = meta.getSort();
		      String realCursor = meta.getCursor();
		      String realFq = meta.getFq();
		      String realFl = meta.getFl();
		      String realFacetQuery = meta.getFacetQuery();
		      String realFacetField = meta.getFacetField();
			  /* Send and Get the report */
		      SolrQuery query = new SolrQuery();
		      query.set("rows", chunkRowSize);
		      if ( realQ != null && !realQ.equals("") ){
		    	  query.set("q", realQ);
		      }
		      if ( realSort != null && !realSort.equals("") ){
		    	  query.set("sort", realSort);
		      } else {
		    	  tryCursor = false;
		      }
		      if ( realCursor != null && !realCursor.equals("") ){
		    	  if( realCursor.equals("No") ){
		    		  tryCursor = false;
		    	  }
		      }
		      if ( realFl != null && !realFl.equals("") ){
		    	  query.set("fl", realFl);
		      }
		      if ( realFq != null && !realFq.equals("") ){
		    	  query.set("fq", realFq);
		      }
		      if ( realFacetField != null && !realFacetField.equals("")){
		    	  //TODO incorporate multiple facet fields at once
		    	  //String[] facetFields = realFacetField.split("\\s*,\\s*");
		    	  //for(int i =0; i < facetFields.length; i++){
		    	  query.addFacetField(realFacetField);  
		    	  //}
	    		  query.setFacet(true);
	    		  query.setFacetLimit(-1);
	    		  query.setFacetMinCount(0);
	    		  query.setFacetSort("count");
	    		  query.set("rows", 0);
	    		  tryCursor = false;
	    		  data.facetRequested = true;
		      }
		      if ( realFacetQuery != null && !realFacetQuery.equals("")){
		    	  query.addFacetQuery(realFacetQuery);
		      }
		      // You can't use "TimeAllowed" with "CursorMark"
		      // The documentation says "Values <= 0 mean 
		      // no time restriction", so setting to 0.
		      query.setTimeAllowed(0);
		      HttpSolrServer solr = new HttpSolrServer(realURL);
		      String cursorMark = CursorMarkParams.CURSOR_MARK_START;
		      boolean done = false;
		      QueryResponse rsp = null;
		      while (!done) {
		    	  if(tryCursor){
	    		  	query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
		    	  } else {
		    		query.setStart(startRecord);
		    	  }
		        try {
		          rsp = solr.query(query);
		        } catch (SolrServerException e) {
		          e.printStackTrace();
		        }
		        if(data.facetRequested){
		        	data.facetCountName = rsp.getFacetFields().get(0).getName();
		        	data.facetCounts = rsp.getFacetFields().get(0).getValues();
		        	done = true;
		        } else {
			        SolrDocumentList theseDocs = rsp.getResults();
			        for(SolrDocument doc : theseDocs) {
			        	data.documentList.add(doc);
			        }
		        }
		        if(tryCursor){
			        String nextCursorMark = rsp.getNextCursorMark();
			        if (cursorMark.equals(nextCursorMark)) {
			          done = true;
			        } else {
			          cursorMark = nextCursorMark;
			        }
		        } else {
			        startRecord = startRecord + chunkRowSize;
			        if(startRecord >= rsp.getResults().getNumFound()){
			        	done = true;
			        }
		        }
		      }
		    }
		      
		    Object[] outputRowData = null;
		    
		    try {
		        if (data.facetRequested){
			        // get one row if we can
			        if ( data.facetCounts.size()-1 < data.recordIndex ) {
				          setOutputDone();
				          return false;
				    }
		        	FacetField.Count facetRecord = data.facetCounts.get(data.recordIndex);
			    	outputRowData = prepareFacetRecord(facetRecord);
		        } else {
			        // get one row if we can
			        if ( data.documentList.size()-1 < data.recordIndex ) {
				          setOutputDone();
				          return false;
				    }
			    	SolrDocument record = data.documentList.get(data.recordIndex);
			    	outputRowData = prepareRecord(record);
		        }
		        putRow( data.outputRowMeta, outputRowData ); // copy row to output rowset(s);
		        data.recordIndex++;
		        return true;
		      } catch ( KettleException e ) {
		        boolean sendToErrorRow = false;
		        String errorMessage = null;
		        if ( getStepMeta().isDoingErrorHandling() ) {
		          sendToErrorRow = true;
		          errorMessage = e.toString();
		        } else {
		          logError( BaseMessages.getString( PKG, "SolrInput.log.Exception", e.getMessage() ) );
		          logError( Const.getStackTracker( e ) );
		          setErrors( 1 );
		          stopAll();
		          setOutputDone(); // signal end to receiver(s)
		          return false;
		        }
		        if ( sendToErrorRow ) {
		          // Simply add this row to the error row
		          putError( getInputRowMeta(), outputRowData, 1, errorMessage, null, "SolrInput001" );
		        }
		      }
		      return true;
		    }
		  
		  private Object[] prepareRecord(SolrDocument record) throws KettleException {
		    // Build an empty row based on the meta-data
		    Object[] outputRowData = buildEmptyRow();
	   	    java.util.Collection<String> thisNamesArray = record.getFieldNames();
	   	    List<String> a = new ArrayList<String>(thisNamesArray);
		    try {
		      for ( int i = 0; i < data.nrfields; i++ ) {
		    	  String value = "";
		    	  if(a.contains(meta.getInputFields()[i].getName())){
		    		  value = record.getFieldValue(meta.getInputFields()[i].getName()).toString();
		    	  }
		        switch ( meta.getInputFields()[i].getTrimType() ) {
		          case SolrInputField.TYPE_TRIM_LEFT:
		            value = Const.ltrim( value );
		            break;
		          case SolrInputField.TYPE_TRIM_RIGHT:
		            value = Const.rtrim( value );
		            break;
		          case SolrInputField.TYPE_TRIM_BOTH:
		            value = Const.trim( value );
		            break;
		          default:
		            break;
		        }
		        // do conversions
		        ValueMetaInterface targetValueMeta = data.outputRowMeta.getValueMeta( i );
		        ValueMetaInterface sourceValueMeta = data.convertRowMeta.getValueMeta( i );
		        outputRowData[i] = targetValueMeta.convertData( sourceValueMeta, value );
		      } // End of loop over fields...
		      RowMetaInterface irow = getInputRowMeta();
		      data.previousRow = irow == null ? outputRowData : irow.cloneRow( outputRowData ); // copy it to make
		    } catch ( Exception e ) {
		      throw new KettleException( BaseMessages
		        .getString( PKG, "SolrInput.Exception.CanNotParseFromSolr" ), e );
		    }
		    return outputRowData;
		  }
		  
		  private Object[] prepareFacetRecord(FacetField.Count facetRecord) throws KettleException {
			    // Build an empty row based on the meta-data
			    Object[] outputRowData = buildEmptyRow();
			    try {
			      for ( int i = 0; i < data.nrfields; i++ ) {
			    	  String value = "";
			    	  if(meta.getInputFields()[i].getName().equals(data.facetCountName)){
			    		  value = facetRecord.getName();
			    	  }
			    	  if(meta.getInputFields()[i].getName().equals("count")){
			    		  value = Long.toString(facetRecord.getCount());
			    	  }
			        switch ( meta.getInputFields()[i].getTrimType() ) {
			          case SolrInputField.TYPE_TRIM_LEFT:
			            value = Const.ltrim( value );
			            break;
			          case SolrInputField.TYPE_TRIM_RIGHT:
			            value = Const.rtrim( value );
			            break;
			          case SolrInputField.TYPE_TRIM_BOTH:
			            value = Const.trim( value );
			            break;
			          default:
			            break;
			        }
			        // do conversions
			        ValueMetaInterface targetValueMeta = data.outputRowMeta.getValueMeta( i );
			        ValueMetaInterface sourceValueMeta = data.convertRowMeta.getValueMeta( i );
			        outputRowData[i] = targetValueMeta.convertData( sourceValueMeta, value );
			      } // End of loop over fields...
			      RowMetaInterface irow = getInputRowMeta();
			      data.previousRow = irow == null ? outputRowData : irow.cloneRow( outputRowData ); // copy it to make
			    } catch ( Exception e ) {
			      throw new KettleException( BaseMessages
			        .getString( PKG, "SolrInput.Exception.CanNotParseFromSolr" ), e );
			    }
			    return outputRowData;
			  }

	
	  /**
	   * Build an empty row based on the meta-data.
	   *
	   * @return
	   */
	  private Object[] buildEmptyRow() {
	    Object[] rowData = RowDataUtil.allocateRowData( data.outputRowMeta.size() );
	    return rowData;
	  }
	
	/**
	 * This method is called by PDI during transformation startup. 
	 * 
	 * It should initialize required for step execution. 
	 * 
	 * The meta and data implementations passed in can safely be cast
	 * to the step's respective implementations. 
	 * 
	 * It is mandatory that super.init() is called to ensure correct behavior.
	 * 
	 * Typical tasks executed here are establishing the connection to a database,
	 * as wall as obtaining resources, like file handles.
	 * 
	 * @param smi 	step meta interface implementation, containing the step settings
	 * @param sdi	step data interface implementation, used to store runtime information
	 * 
	 * @return true if initialization completed successfully, false if there was an error preventing the step from working. 
	 *  
	 */
	  public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
		
	    meta = (SolrInputMeta) smi;
	    data = (SolrInputData) sdi;

	    if ( super.init( smi, sdi ) ) {
	      // get total fields in the grid
	      data.nrfields = meta.getInputFields().length;
	      // Check if field list is filled
	      if ( data.nrfields == 0 ) {
	        log.logError( BaseMessages.getString( PKG, "SolrInputDialog.FieldsMissing.DialogMessage" ) );
	        return false;
	      }
	      // check url
	      String realURL = environmentSubstitute( meta.getURL() );
	      //if ( Const.isEmpty( realURL ) ) {
	      if (realURL.isEmpty()) {
	        log.logError( BaseMessages.getString( PKG, "SolrInput.UsernameMissing.Error" ) );
	        return false;
	      }
	      try{
	    	data.solr = new HttpSolrServer(realURL);
	        return true;
	      }  catch ( Exception e ) {
              log.logError( BaseMessages.getString( PKG, "SolrInput.Log.ErrorOccurredDuringStepInitialize" ), e );
          }
	      return true;
	    }
	    return false;
	  }

	/**
	 * This method is called by PDI once the step is done processing. 
	 * 
	 * The dispose() method is the counterpart to init() and should release any resources
	 * acquired for step execution like file handles or database connections.
	 * 
	 * The meta and data implementations passed in can safely be cast
	 * to the step's respective implementations. 
	 * 
	 * It is mandatory that super.dispose() is called to ensure correct behavior.
	 * 
	 * @param smi 	step meta interface implementation, containing the step settings
	 * @param sdi	step data interface implementation, used to store runtime information
	 */
	public void dispose(StepMetaInterface smi, StepDataInterface sdi) {

		// Casting to step-specific implementation classes is safe
		meta = (SolrInputMeta) smi;
		data = (SolrInputData) sdi;
		
	    try {
	        if ( data.outputRowMeta != null ) {
	          data.outputRowMeta = null;
	        }
	        if ( data.convertRowMeta != null ) {
	          data.convertRowMeta = null;
	        }
	    } catch ( Exception e ) { /* Ignore */
	    }
	    super.dispose( smi, sdi );
	  }
	
}
