package it.unibz.inf.ontop.r2rml;

import eu.optique.api.mapping.TriplesMap;
import it.unibz.inf.ontop.exception.DuplicateMappingException;
import it.unibz.inf.ontop.exception.InvalidMappingException;
import it.unibz.inf.ontop.injection.NativeQueryLanguageComponentFactory;
import it.unibz.inf.ontop.injection.OBDACoreConfiguration;
import it.unibz.inf.ontop.injection.OBDASettings;
import it.unibz.inf.ontop.model.OBDAModel;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;

public class OBDAMappingTransformerDebugTest {

    @Test
    public void testMultipleSubjectsInMappingTarget() throws IOException, InvalidMappingException, DuplicateMappingException {
        File mapFile = new File("src/test/resources/obdaMappingTransformerTests/splitMappingAxiomBySubject.obda");

        OBDACoreConfiguration config = OBDACoreConfiguration.defaultBuilder()
                .properties(generateProperties())
                .nativeOntopMappingFile(mapFile)
                .build();

        OBDAModel model = config.loadProvidedPPMapping();

        R2RMLWriter writer = new R2RMLWriter(model, null, config.getInjector().getInstance(
                NativeQueryLanguageComponentFactory.class));

        Collection<TriplesMap> tripleMaps = writer.getTripleMaps();
        for (TriplesMap tripleMap : tripleMaps) {
            System.out.println(tripleMap);
        }
        assertTrue(tripleMaps.size() > 1);
    }

    @Test
    public void testPredicateMapTranslation() throws IOException, InvalidMappingException, DuplicateMappingException {
        File mapFile = new File("src/test/resources/obdaMappingTransformerTests/predicateMap.obda");
        OBDACoreConfiguration config = OBDACoreConfiguration.defaultBuilder()
                .properties(generateProperties())
                .nativeOntopMappingFile(mapFile)
                .build();

        OBDAModel model = config.loadProvidedPPMapping();

        R2RMLWriter writer = new R2RMLWriter(model,null, config.getInjector().getInstance(
                NativeQueryLanguageComponentFactory.class));

        assertTrue(writer.getTripleMaps().stream().findFirst()
                .filter(m -> m.getPredicateObjectMap(0).getPredicateMap(0).getTemplate() != null)
                .isPresent());
    }

    private static Properties generateProperties() {
        Properties p = new Properties();
        p.setProperty(OBDASettings.JDBC_NAME, "DBName");
        p.setProperty(OBDASettings.JDBC_URL, "jdbc:h2:tcp://localhost/DBName");
        p.setProperty(OBDASettings.JDBC_USER, "sa");
        p.setProperty(OBDASettings.JDBC_PASSWORD, "");
        p.setProperty(OBDASettings.JDBC_DRIVER, "com.mysql.jdbc.Driver");
        return p;
    }
}