 /* SpagoBI, the Open Source Business Intelligence suite

 * Copyright (C) 2012 Engineering Ingegneria Informatica S.p.A. - SpagoBI Competency Center
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0, without the "Incompatible With Secondary Licenses" notice. 
 * If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package it.eng.spagobi.tools.dataset.measurecatalogue.services;

import it.eng.spago.security.IEngUserProfile;
import it.eng.spagobi.commons.utilities.StringUtilities;
import it.eng.spagobi.commons.utilities.UserUtilities;
import it.eng.spagobi.services.exceptions.ExceptionUtilities;
import it.eng.spagobi.tools.dataset.common.datastore.IDataStore;
import it.eng.spagobi.tools.dataset.common.datawriter.JSONDataWriter;
import it.eng.spagobi.tools.dataset.common.metadata.IFieldMetaData;
import it.eng.spagobi.tools.dataset.common.metadata.IFieldMetaData.FieldType;
import it.eng.spagobi.tools.dataset.measurecatalogue.MeasureCatalogue;
import it.eng.spagobi.tools.dataset.measurecatalogue.MeasureCatalogueMeasure;
import it.eng.spagobi.tools.dataset.measurecatalogue.MeasureCatalogueSingleton;
import it.eng.spagobi.tools.dataset.measurecatalogue.materializer.InMemoryMaterializer;
import it.eng.spagobi.tools.dataset.measurecatalogue.materializer.exception.NoCommonDimensionsRuntimeException;
import it.eng.spagobi.tools.dataset.measurecatalogue.materializer.exception.NoCompleteCommonDimensionsRuntimeException;
import it.eng.spagobi.utilities.assertion.Assert;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class contains the services to perform the CRUD action on the measure catalogue
 * @author Alberto Ghedin (alberto.ghedin@eng.it)
 *
 */

@Path("/measures")
public class MeasureCatalogueCRUD {
	
	static private String noCommonDimensionsRuntimeException = "error.mesage.description.measure.join.no.common.dimension";
	static private String noCompleteCommonDimensionsRuntimeException = "error.mesage.description.measure.join.no.complete.common.dimension";
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getAllMeasures(@Context HttpServletRequest req) {
		IEngUserProfile profile = (IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE);
		String measures =  MeasureCatalogueSingleton.getMeasureCatologue().toString(profile.getUserUniqueIdentifier().toString(), UserUtilities.isAdministrator(profile));
		return measures;
	}
	
	@POST
	@Path("/join")
	@Consumes("application/x-www-form-urlencoded")
	@Produces(MediaType.APPLICATION_JSON)
	public String join(@Context HttpServletRequest req, MultivaluedMap<String, String> form) {
		MeasureCatalogue catalogue = MeasureCatalogueSingleton.getMeasureCatologue();
		IDataStore dataStore;
		
		List<String> ids = form.get("ids");
		
		List<MeasureCatalogueMeasure> measures= new ArrayList<MeasureCatalogueMeasure>();
		
		for(int i=0; i<ids.size(); i++){
			MeasureCatalogueMeasure aMeasure = catalogue.getMeasureById(new Integer(ids.get(i)));
			if(aMeasure!=null){
				measures.add(aMeasure);
			}
		}

		InMemoryMaterializer imm = new InMemoryMaterializer();
		try {
			dataStore =  imm.joinMeasures(measures);
		} catch (NoCommonDimensionsRuntimeException e) {
			return ( ExceptionUtilities.serializeException(noCommonDimensionsRuntimeException,null));
		} catch (NoCompleteCommonDimensionsRuntimeException e) {
			return ( ExceptionUtilities.serializeException(noCompleteCommonDimensionsRuntimeException,null));
		}
		
		
		

		

		JSONDataWriter dataSetWriter = new JSONDataWriter();
		JSONObject dataStroreJSON =  (JSONObject) dataSetWriter.write(dataStore);
		JSONObject metaData;
		
		try {
			metaData = dataStroreJSON.getJSONObject("metaData");
			JSONArray fieldsMetaJSON = metaData.getJSONArray("fields");
			List<IFieldMetaData> geoRefFieldMeta = new ArrayList<IFieldMetaData>();
			for(int i = 0; i < dataStore.getMetaData().getFieldCount(); i++) {
				IFieldMetaData fieldMeta = dataStore.getMetaData().getFieldMeta(i);
				JSONObject fieldMetaJSON = fieldsMetaJSON.getJSONObject(i+1);
				if(fieldMeta.getFieldType().equals(FieldType.MEASURE)){
					fieldMetaJSON.put("role", "MEASURE");
				} else if(fieldMeta.getFieldType().equals(FieldType.ATTRIBUTE)){
					fieldMetaJSON.put("role", "ATTRIBUTE");
					String hierarchy = (String)fieldMeta.getProperty("hierarchy");
					if(hierarchy != null) {
						fieldMetaJSON.put("hierarchy", hierarchy);
						fieldMetaJSON.put("hierarchy_level", (String)fieldMeta.getProperty("hierarchy_level"));
						if(hierarchy.equalsIgnoreCase("GEO")) {
							geoRefFieldMeta.add(fieldMeta);
						}
					}
				}
				
				fieldMetaJSON.put("naturalKey", fieldMeta.getName());
				fieldMetaJSON.put("dataset", fieldMeta.getProperty("dataset"));
			}
			
			if(geoRefFieldMeta.size() == 0) {
				throw new RuntimeException("Internal server error: generated dataset have no reference to geographical dimension");
			} 
			if(geoRefFieldMeta.size() > 1) {
				throw new RuntimeException("Internal server error: generated dataset have more than one reference to geographical dimension");
			} 
			
			metaData.put("geoId", geoRefFieldMeta.get(0).getName());
			metaData.put("geoIdHierarchyLevel", geoRefFieldMeta.get(0).getProperty("hierarchy_level"));
			// TODO bisogna strippare la label del datset dal prefisso dell'header di colonna
		} catch (JSONException t) {
			// TODO Auto-generated catch block
			t.printStackTrace();
		}
		
		
		return  dataStroreJSON.toString();
	}
	

}
