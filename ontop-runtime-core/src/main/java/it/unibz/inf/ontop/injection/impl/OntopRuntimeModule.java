package it.unibz.inf.ontop.injection.impl;


import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.util.Providers;
import it.unibz.inf.ontop.injection.OntopRuntimeConfiguration;
import it.unibz.inf.ontop.injection.OntopRuntimeSettings;
import it.unibz.inf.ontop.injection.ReformulationFactory;
import it.unibz.inf.ontop.reformulation.IRIDictionary;
import it.unibz.inf.ontop.reformulation.unfolding.QueryUnfolder;

import java.util.Optional;

public class OntopRuntimeModule extends OntopAbstractModule {
    // Temporary
    private OntopRuntimeConfiguration configuration;

    protected OntopRuntimeModule(OntopRuntimeConfiguration configuration) {
        super(configuration.getSettings());
        this.configuration = configuration;
    }

    @Override
    protected void configure() {
        bind(OntopRuntimeSettings.class).toInstance(configuration.getSettings());

        Optional<IRIDictionary> iriDictionary = configuration.getIRIDictionary();
        if (iriDictionary.isPresent()) {
            bind(IRIDictionary.class).toInstance(iriDictionary.get());
        }
        else {
            bind(IRIDictionary.class).toProvider(Providers.of(null));
        }

        Module reformulationFactoryModule = buildFactory(
                ImmutableList.of(
                        QueryUnfolder.class),
                ReformulationFactory.class);

        install(reformulationFactoryModule);
        configuration = null;
    }
}