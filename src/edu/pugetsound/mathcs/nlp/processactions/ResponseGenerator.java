package edu.pugetsound.mathcs.nlp.processactions;

import java.lang.Thread;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;


import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;


import edu.pugetsound.mathcs.nlp.lang.Utterance;
import edu.pugetsound.mathcs.nlp.lang.Conversation;
import edu.pugetsound.mathcs.nlp.features.TextAnalyzer;


//Requires Jython 2.5: http://www.jython.org/
//http://search.maven.org/remotecontent?filepath=org/python/jython-standalone/2.7.0/jython-standalone-2.7.0.jar
import org.python.util.PythonInterpreter;
import org.python.core.PyList;
import org.python.core.PyString;
import org.python.core.PyInteger;


import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.DocumentPreprocessor;


/**
 * The main response generator of the Process Actions step
 * This class should only be used to access the method generateResponse(...);
 * @author Thomas Gagne
 */
public class ResponseGenerator {


    private static String[] getUtterances(File f) throws FileNotFoundException,IOException {
        DocumentPreprocessor dp = new DocumentPreprocessor(
            new BufferedReader(
                new FileReader(f)));
        ArrayList<String> sentences = new ArrayList<String>();
        String sentence;
        for (List<HasWord> hw: dp){
            sentence = "";
            for (HasWord w: hw)
                sentence+=w.word()+" ";
            sentences.add(sentence);
        }
        String[] utts = new String[sentences.size()];
        return sentences.toArray(utts);
    }

    private static int saveUtterancesWithAnalysis(String[] utterances, String outfileName) {
        Conversation tempConvo;
        ArrayList<Utterance> convoList = new ArrayList<Utterance>();
        PyString[] tokens;
        TextAnalyzer ta = new TextAnalyzer();
        PythonInterpreter python = new PythonInterpreter();
        python.execfile("../scripts/responseTemplater.py");            
        for (int p=0; p<utterances.length; p++) {
            try {
                verifyLists(python);

                if (p > 15 && (p % 15) == 0){
                    System.out.println("Writing current progress to file since we've analyzed "+p+" paragraphs.");
                    python.exec("main(fn)");
                }

                tempConvo = new Conversation();
                for (Utterance u: convoList)
                    tempConvo.addUtterance(u);
                Utterance currentUtt = ta.analyze(utterances[p], tempConvo);
                convoList.add(currentUtt);
                if (convoList.size() > 10)
                    convoList.remove(0);
                python.set("dat", new PyString(currentUtt.daTag.toString()));
                python.exec("DATags.append(dat)");
                python.set("amr", new PyString(currentUtt.amr.toString()));
                python.exec("AMRs.append(amr)");

                tokens = new PyString[currentUtt.tokens.size()];
                for (int i=0; i<currentUtt.tokens.size(); i++)
                    tokens[i] = new PyString(currentUtt.tokens.get(i).token);
                python.set("ts", new PyList(tokens));
                python.exec("tokens.append(ts)");

                System.out.println("Done asking the TextAnalyzer to analyze each utterance with an AMR/DATag/tokens.");
            } catch(Exception e) {
                System.out.println("Issue with paragraph "+p+"; reverting any added amrs/utterances/datags to last paragraph.");
                System.out.println(e);
            }
        }
        python.exec("tokensLen = len(tokens)");
        int tokensLen = ((PyInteger) python.get("tokensLen")).asInt();
        System.out.println("Now writing "+tokensLen+" results to output file at "+outfileName);
        python.set("fn", new PyString(outfileName));
        python.exec("main(fn)");

        return tokensLen;
    }

    private static void verifyLists(PythonInterpreter python) {

        python.exec("tokensLen = len(tokens)");
        int tokensLen = ((PyInteger) python.get("tokensLen")).asInt();
        python.exec("amrLen = len(AMRs)");
        int amrLen = ((PyInteger) python.get("amrLen")).asInt();
        python.exec("daTagsLen = len(DATags)");
        int daTagsLen = ((PyInteger) python.get("daTagsLen")).asInt();
        
        if (tokensLen != amrLen)
            if (tokensLen > amrLen) {
                python.exec("utterances = utterances[:amrLen]");
                python.exec("tokensLen = len(tokens)");
                tokensLen = ((PyInteger) python.get("tokensLen")).asInt();
            }
            else {
                python.exec("AMRs = AMRs[:tokensLen]");
                python.exec("amrLen = len(AMRs)");
                amrLen = ((PyInteger) python.get("amrLen")).asInt();
            }
        if (tokensLen > daTagsLen){
            python.exec("utterances = utterances[:daTagsLen]");
            python.exec("tokensLen = len(tokens)");
            tokensLen = ((PyInteger) python.get("tokensLen")).asInt();
        }
        if (amrLen > daTagsLen){
            python.exec("AMRs = AMRs[:daTagsLen]");
            python.exec("amrLen = len(AMRs)");
            amrLen = ((PyInteger) python.get("amrLen")).asInt();
        }
    }



    public static void main(String a[]) {
        ArrayList<String> args = new ArrayList<String>(Arrays.asList(a));
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println( "This script will read a text file of utterances and generate"
                +" responses from them for the processActions templates to use."
                +"Now, we can make this bot talk like Abe Lincoln or Darth Vader or Donald Trump!!!"
                +"Make NLP great again!\n\n"
                +"Usage:\n\tjava -cp ClasspathToJars edu.pugetsound.mathcs."
                +"nlp.processactions.ResponseGenerator args inputFile.txt outputFile.txt\n"
                +"inputFile.txt: The exact path to the file to be used as input\n"
                +"outputFile.txt: The name of the file to be writen to as output, within the "
                +"processactions/srt folder. Defaults to responses.json. If file exists, it is read "
                +"from and responses are added to it.\n"
                +"Args:\n\t-h, --help: Display this message" );
        } 
        else if (args.size() > 2 || args.size() == 0){
            System.out.println("Error with arguments: need one or two. You provided "+args.size());
        } 
        else {
            File inputFile = new File(args.get(0));
            if(!inputFile.exists() || inputFile.isDirectory()) {
                System.out.println("Error with first arg: not a valid file");
            } 
            else {
                if (args.size() == 1)
                    args.add("responses.json");

                try {

                    System.out.println("Reading from input file at "+inputFile.getAbsolutePath());
                    String[] utterances = getUtterances(inputFile);
                    System.out.println("Done reading from input file.");
                    int tokensLen = saveUtterancesWithAnalysis(utterances, "../src/edu/pugetsound/mathcs/nlp/processactions/srt/" + args.get(1));
                    if (tokensLen > 0) {
                        System.out.println("Done writing results to output file!");
                    }
                    else {
                        System.out.println("Warning: no utterances/AMRs found. Not writing anything to output file.");
                    }

                } catch(FileNotFoundException ex) {
                    System.out.println("Error: Unable to open file");    
                    ex.printStackTrace();            
                } catch (IOException e) {
                    System.out.println("Error: IOException");
                    e.printStackTrace();
                } 
            }
        }
    }       

}