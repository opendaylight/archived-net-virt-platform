/*
 * Copyright (c) 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.sdnplatform.doc;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.sdnplatform.core.annotations.LogMessageCategory;
import org.sdnplatform.core.annotations.LogMessageDoc;
import org.sdnplatform.core.annotations.LogMessageDocs;


/**
 * Tool to process LogMessage annotations and generate documentation
 * @author readams
 */
@SupportedAnnotationTypes(value={"org.sdnplatform.core.annotations.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedOptions("outputFile")
public class LogMessageDocProcessor extends AbstractProcessor {
    private PrintStream out = System.out;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        Map<String,String> options = processingEnv.getOptions();
        for (String s : options.keySet()) {
            if (s.equals("outputFile")) {
                try {
                    String opt = options.get(s);
                    OutputStream os = new FileOutputStream(opt);
                    out = new PrintStream(os);
                    System.err.println("Setting output file to " + opt);
                } catch (FileNotFoundException e) {
                    System.err.println("Error count not open " + 
                                       options.get(s));
                    System.exit(1);
                }
            }
        }
        
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        
        for (TypeElement element : annotations){
            for (Element e: roundEnv.getElementsAnnotatedWith(element)){
                processElement(e);
            }
        }
        
        return true;
    }

    protected void processElement(Element element) {
        LogMessageDocs ms = element.getAnnotation(LogMessageDocs.class);
        if (ms != null) {
            for (LogMessageDoc m : ms.value())
                processLogMessage(m, element);
        }
        LogMessageDoc m = element.getAnnotation(LogMessageDoc.class);
        if (m != null)
            processLogMessage(m, element);
    }
    
    protected void processLogMessage(LogMessageDoc m, Element element) {
        Element encElement = element.getEnclosingElement();
        LogMessageCategory category = 
                element.getAnnotation(LogMessageCategory.class);
        if (category == null)
            category = encElement.getAnnotation(LogMessageCategory.class);

        String categoryStr = "Core";
        if (category != null) categoryStr = category.value();
        
        LogMessageDocItemData lmdi = new LogMessageDocItemData();
        lmdi.className = encElement.asType().toString();
        lmdi.category = categoryStr;
        lmdi.severity = m.level();
        lmdi.message = m.message();
        lmdi.explanation = m.explanation();
        lmdi.recommendation = m.recommendation();

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.defaultPrettyPrintingWriter();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            writer.writeValue(os, lmdi);
            out.print(os.toString());
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

}
