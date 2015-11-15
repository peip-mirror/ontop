package it.unibz.krdb.obda.owlapi3.directmapping;

/*
 * #%L
 * ontop-obdalib-owlapi3
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

import it.unibz.krdb.obda.model.*;
import it.unibz.krdb.obda.model.Predicate.COL_TYPE;
import it.unibz.krdb.obda.utils.JdbcTypeMapper;
import it.unibz.krdb.sql.Attribute;
import it.unibz.krdb.sql.ForeignKeyConstraint;
import it.unibz.krdb.sql.RelationID;
import it.unibz.krdb.sql.DatabaseRelationDefinition;
import it.unibz.krdb.sql.UniqueConstraint;
import it.unibz.krdb.sql.ForeignKeyConstraint.Component;

import java.util.*;

import com.google.common.base.Joiner;

public class DirectMappingAxiom {
	private final String baseuri;
	private final OBDADataFactory df;

	public DirectMappingAxiom(String baseuri, OBDADataFactory dfac) {
		this.df = dfac;
		if (baseuri == null)
			throw new IllegalArgumentException("Base uri must be specified!");

		this.baseuri = baseuri;
	}


	public String getSQL(DatabaseRelationDefinition table) {
		return String.format("SELECT * FROM %s", table.getID().getSQLRendering());
	}

	public Map<String, List<Function>> getRefAxioms(DatabaseRelationDefinition table) {
		Map<String, List<Function>> refAxioms = new HashMap<>();
		for (ForeignKeyConstraint fk : table.getForeignKeys()) 
			refAxioms.put(getRefSQL(fk), getRefCQ(fk));
		
		return refAxioms;
	}

	private String getRefSQL(ForeignKeyConstraint fk) {

		Set<Object> columns = new LinkedHashSet<>(); // Set avoids duplicated and LinkedHashSet keeps the insertion order
		for (Attribute attr : getIdentifyingAttributes(fk.getRelation())) 
			columns.add(getColumnNameWithAlias(attr));

		List<String> conditions = new ArrayList<>(fk.getComponents().size());	
		for (ForeignKeyConstraint.Component comp : fk.getComponents()) {
			columns.add(getColumnNameWithAlias(comp.getReference()));	
			conditions.add(getColumnName(comp.getAttribute()) + " = " + getColumnName(comp.getReference()));
		}
		
		for (Attribute attr : getIdentifyingAttributes(fk.getReferencedRelation())) 
			columns.add(getColumnNameWithAlias(attr));

		final String tables = fk.getRelation().getID().getSQLRendering() + 
							", " + fk.getReferencedRelation().getID().getSQLRendering();
		
		return String.format("SELECT %s FROM %s WHERE %s", 
				Joiner.on(", ").join(columns), tables, Joiner.on(" AND ").join(conditions));
	}

	private static List<Attribute> getIdentifyingAttributes(DatabaseRelationDefinition table) {
		UniqueConstraint pk = table.getPrimaryKey();
		if (pk != null)
			return pk.getAttributes();
		else
			return table.getAttributes();
	}
	
	private static String getColumnNameWithAlias(Attribute attr) {
		 return getColumnName(attr) + 
				 " AS " + attr.getRelation().getID().getTableName() + "_" + attr.getID().getName();
	}
	
	private static String getColumnName(Attribute attr) {
		 return attr.getQualifiedID().getSQLRendering();
	}
	
	public List<Function> getCQ(DatabaseRelationDefinition table) {

		List<Function> atoms = new ArrayList<>(table.getAttributes().size() + 1);

		//Class Atom
		Term sub = generateSubject(table, false);
		atoms.add(df.getFunction(df.getClassPredicate(generateClassURI(table.getID())), sub));

		//DataType Atoms
		JdbcTypeMapper typeMapper = df.getJdbcTypeMapper();
		for (Attribute att : table.getAttributes()) {
			Predicate.COL_TYPE type = typeMapper.getPredicate(att.getType());
			Variable objV = df.getVariable(att.getID().getName());
			Term obj;
			if (type == COL_TYPE.LITERAL) 
				obj = objV;
			else 
				obj = df.getTypedTerm(objV, type);
			
			atoms.add(df.getFunction(df.getDataPropertyPredicate(generateDPURI(att)), sub, obj));
		}

		return atoms;
	}

	private List<Function> getRefCQ(ForeignKeyConstraint fk) {
        Term sub = generateSubject(fk.getRelation(), true);
		Term obj = generateSubject(fk.getReferencedRelation(), true);

		Function atom = df.getFunction(df.getObjectPropertyPredicate(generateOPURI(fk)), sub, obj);
		return Collections.singletonList(atom);
	}

	// Generate an URI for class predicate from a string(name of table)
	private String generateClassURI(RelationID tableId) {
		return baseuri + tableId.getTableName(); // ROMAN 15 Nov 2015: need to percent encode?
	}

	/*
	 * Generate an URI for datatype property from a string(name of column) The
	 * style should be "baseuri/tablename#columnname" as required in Direct
	 * Mapping Definition
	 */
	private String generateDPURI(Attribute attr) {
		return baseuri + percentEncode(attr.getRelation().getID().getTableName()) + "#" + percentEncode(attr.getID().getName());
	}

    /*
     * Generate an URI for object property from a string(name of column)
     *
     * <http://www.w3.org/TR/rdb-direct-mapping/>
     *
     * Definition reference property IRI: the concatenation of:
     *   - the percent-encoded form of the table name,
     *   - the string '#ref-',
     *   - for each column in the foreign key, in order:
     *     - the percent-encoded form of the column name,
     *     - if it is not the last column in the foreign key, a SEMICOLON character ';'
     */
    private String generateOPURI(ForeignKeyConstraint fk) {
        List<String> attributes = new ArrayList<>(fk.getComponents().size());
 		for (Component component : fk.getComponents())
            attributes.add(component.getAttribute().getID().getName()); // ROMAN 15 Nov 2015: need to percent encode?
        
        return baseuri + percentEncode(fk.getRelation().getID().getTableName()) + "#ref-" + Joiner.on(";").join(attributes);
    }

    /**
     * - If the table has a primary key, the row node is a relative IRI obtained by concatenating:
     *   - the percent-encoded form of the table name,
     *   - the SOLIDUS character '/',
     *   - for each column in the primary key, in order:
     *     - the percent-encoded form of the column name,
     *     - a EQUALS SIGN character '=',
     *     - the percent-encoded lexical form of the canonical RDF literal representation of the column value as defined in R2RML section 10.2 Natural Mapping of SQL Values [R2RML],
     *     - if it is not the last column in the primary key, a SEMICOLON character ';'
     * - If the table has no primary key, the row node is a fresh blank node that is unique to this row.
     *
     * @param td
     * @return
     */

    private Term generateSubject(DatabaseRelationDefinition td, boolean ref) {
		
		String varNamePrefix = "";
		if (ref)
			varNamePrefix = percentEncode(td.getID().getTableName()) + "_"; // ROMAN 15 Nov: why is this percent encoded?
		                                                                // is this not the name of the alias?

		UniqueConstraint pk = td.getPrimaryKey();
		if (pk != null) {
			List<Term> terms = new ArrayList<>(pk.getAttributes().size() + 1);
			
			List<String> attributes = new ArrayList<>(pk.getAttributes().size());
			for (Attribute att : pk.getAttributes()) 
				attributes.add(percentEncode(att.getID().getName()) + "={}");
			
			String template = baseuri + percentEncode(td.getID().getTableName()) + "/" + Joiner.on(";").join(attributes);
			terms.add(df.getConstantLiteral(template));
			
			for (Attribute att : pk.getAttributes())
				terms.add(df.getVariable(varNamePrefix + att.getID().getName()));

			return df.getUriTemplate(terms);
		}
		else {
			List<Term> vars = new ArrayList<>(td.getAttributes().size());
			for (Attribute att : td.getAttributes())
				vars.add(df.getVariable(varNamePrefix + att.getID().getName()));

			return df.getBNodeTemplate(vars);
		}
	}


	/*
	 * percent encoding for a String
	 */
	private static String percentEncode(String pe) {
		pe = pe.replace("#", "%23");
		pe = pe.replace(".", "%2E");
		pe = pe.replace("-", "%2D");
		pe = pe.replace("/", "%2F");

		pe = pe.replace(" ", "%20");
		pe = pe.replace("!", "%21");
		pe = pe.replace("$", "%24");
		pe = pe.replace("&", "%26");
		pe = pe.replace("'", "%27");
		pe = pe.replace("(", "%28");
		pe = pe.replace(")", "%29");
		pe = pe.replace("*", "%2A");
		pe = pe.replace("+", "%2B");
		pe = pe.replace(",", "%2C");
		pe = pe.replace(":", "%3A");
		pe = pe.replace(";", "%3B");
		pe = pe.replace("=", "%3D");
		pe = pe.replace("?", "%3F");
		pe = pe.replace("@", "%40");
		pe = pe.replace("[", "%5B");
		pe = pe.replace("]", "%5D");
		return pe;
	}

}
