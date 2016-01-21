package org.semanticweb.ontop.cli;

import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.help.cli.bash.BashCompletionGenerator;
import com.github.rvesse.airline.model.GlobalMetadata;

import java.io.IOException;

public class OntopBashCompletionGenerator {


    public static void main(String[] args) throws IOException {


        GlobalMetadata<OntopCommand> metadata = new Cli<OntopCommand>(Ontop.class).getMetadata();

        //GlobalMetadata<OntopCommand> metadata = Ontop.getOntopCommandCLI().getMetadata();

        BashCompletionGenerator<OntopCommand> generator = new BashCompletionGenerator<>();

        generator.usage(metadata, System.out);
    }
}
