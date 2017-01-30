package it.unibz.inf.ontop.rdf4j.repository;

/*
 * #%L
 * ontop-quest-sesame
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import it.unibz.inf.ontop.injection.QuestConfiguration;
import it.unibz.inf.ontop.model.OBDAException;
import it.unibz.inf.ontop.owlrefplatform.core.QuestConstants;
import it.unibz.inf.ontop.owlrefplatform.core.QuestDBConnection;
import it.unibz.inf.ontop.owlrefplatform.questdb.QuestDBVirtualStore;

import org.eclipse.rdf4j.repository.RepositoryException;
public class OntopVirtualRepository extends AbstractOntopRepository {

	private QuestDBVirtualStore virtualStore;
	private QuestDBConnection questDBConn;
	
	public OntopVirtualRepository(QuestConfiguration configuration) {
		super();
		this.virtualStore = new QuestDBVirtualStore(configuration);
	}
	/**
	 * This method leads to the reasoner being initalized, which includes the call to {@code Quest.setupRepository}: connecting to the database,
	 * analyzing mappings etc. This must be called before any queries are run, i.e. before {@code getQuestConnection}.
	 * 
	 */
	@Override
	public void initialize() throws RepositoryException{
		super.initialize();
		try {
			this.virtualStore.initialize();
		}
		catch (Exception e){
			throw new RepositoryException(e);
		}
	}
	
	/**
	 * Returns a connection which can be used to run queries over the repository
	 * Before this method can be used, {@link initialize()} must be called once.
	 */
	@Override
	public QuestDBConnection getQuestConnection() throws OBDAException {
		if(!super.initialized)
			throw new Error("The OntopVirtualRepository must be initialized before getQuestConnection can be run. See https://github.com/ontop/ontop/wiki/API-change-in-OntopVirtualRepository-and-QuestDBVirtualStore");

		questDBConn = this.virtualStore.getConnection();
		return questDBConn;
	}

	@Override
	public boolean isWritable() throws RepositoryException {
		// Checks whether this repository is writable, i.e.
		// if the data contained in this repository can be changed.
		// The writability of the repository is determined by the writability
		// of the Sail that this repository operates on.
		return false;
	}
	
	@Override
	public void shutDown() throws RepositoryException {
		super.shutDown();
		try {
			questDBConn.close();
			virtualStore.close();
		} catch (OBDAException e) {
			e.printStackTrace();
		}
	}

	public String getType() {
		return QuestConstants.VIRTUAL;
	}


}