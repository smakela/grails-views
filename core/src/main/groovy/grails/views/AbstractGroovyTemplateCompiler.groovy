package grails.views

import grails.views.compiler.ViewsTransform
import grails.views.resolve.GenericGroovyTemplateResolver
import groovy.io.FileType
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.io.FileReaderSource

/**
 * A generic compiler for Groovy templates that are compiled into classes in production
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractGroovyTemplateCompiler {

    @Delegate CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ""
    File sourceDir
    ViewConfiguration viewConfiguration

    AbstractGroovyTemplateCompiler(ViewConfiguration configuration, File sourceDir) {
        this.viewConfiguration = configuration
        this.packageName = configuration.packageName
        this.sourceDir = sourceDir
        configureCompiler()
    }

    AbstractGroovyTemplateCompiler() {
    }

    protected CompilerConfiguration configureCompiler() {
        configuration.compilationCustomizers.clear()

        def importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports( viewConfiguration.packageImports )
        importCustomizer.addStaticStars( viewConfiguration.staticImports )

        configuration.addCompilationCustomizers(importCustomizer)
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(newViewsTransform()))
        return configuration
    }

    protected ViewsTransform newViewsTransform() {
        new ViewsTransform(viewConfiguration.extension)
    }

    void compile(Iterable<File> sources) {
        configuration.setClasspathList(classpath)
        def pathToSourceDir = sourceDir.canonicalPath
        for(source in sources) {
            configureCompiler()
            def unit = new CompilationUnit(configuration)
            def pathToSource = source.canonicalPath
            def path = pathToSource - pathToSourceDir
            def templateName = GenericGroovyTemplateResolver.resolveTemplateName(
                    packageName, path
            )
            unit.addSource(new SourceUnit(
                    templateName,
                    new FileReaderSource(source, configuration),
                    configuration,
                    unit.classLoader,
                    unit.errorCollector
            ))
            unit.compile()
        }

    }

    void compile(File...sources) {
        compile Arrays.asList(sources)
    }

    static void run(String[] args, Class<? extends GenericViewConfiguration> configurationClass, Class<? extends AbstractGroovyTemplateCompiler> compilerClass) {
        if(args.length != 7) {
            System.err.println("Invalid arguments: [${args.join(',')}]")
            System.err.println("""
Usage: java -cp CLASSPATH ${compilerClass.name} [srcDir] [destDir] [targetCompatibility] [packageImports] [packageName] [configFile] [encoding]
""")
            System.exit(1)
        }
        File srcDir = new File(args[0])
        File destinationDir = new File(args[1])
        String targetCompatibility = args[2]
        String[] packageImports = args[3].trim().split(',')
        String packageName = args[4].trim()
        File configFile = new File(args[5])
        String encoding = new File(args[6])

        GenericViewConfiguration configuration = configurationClass.newInstance()
        configuration.packageName = packageName
        configuration.encoding = encoding
        configuration.packageImports = packageImports

        configuration.readConfiguration(configFile)

        AbstractGroovyTemplateCompiler compiler = compilerClass.newInstance(configuration, srcDir)
        compiler.setTargetDirectory( destinationDir )
        compiler.setSourceEncoding( configuration.encoding )
        if(targetCompatibility != null) {
            compiler.setTargetBytecode( targetCompatibility )
        }


        def fileExtension = configuration.extension
        compiler.setDefaultScriptExtension(fileExtension)

        List<File> allFiles = []
        srcDir.eachFileRecurse(FileType.FILES) { File f ->
            if(f.name.endsWith(fileExtension)) {
                allFiles.add(f)
            }
        }
        compiler.compile(allFiles)
    }


}
