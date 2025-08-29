package ui.wordpool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for the wordpool files.
 *
 * <p>Parses both wordpool documents and the narrower word list documents for one audio file (called
 * LST files in PyParse). Words must contain letters or the parser will skip them. The word consists
 * of the entire line it is found on. Indexes are relative to the list of words considered words by
 * this parser, not line numbers or any other standard.
 */
public class WordpoolFileParser {
    private static final Logger logger = LoggerFactory.getLogger(WordpoolFileParser.class);

    /** Private constructor to prevent instantiation. */
    private WordpoolFileParser() {}

    /**
     * Parses the wordpool file, traversing it line by line.
     *
     * @param file The file to be parsed
     * @return A List containing the WordpoolWords in the same order they appear in the list
     * @throws IOException In the event of i/o problems while reading the File
     */
    public static List<WordpoolWord> parse(File file, boolean suppressLineNumbers)
            throws IOException {
        ArrayList<WordpoolWord> words = new ArrayList<WordpoolWord>();
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++; // PyParse 1-indexes wordpool words by line number
                Matcher whiteSpace = Pattern.compile("\\s*").matcher(line);
                if (whiteSpace.matches()) {
                    logger.warn("line #" + lineNum + " not a valid wordpool word: " + line);
                } else {
                    String trimmed = line.trim();
                    if (suppressLineNumbers == false) {
                        words.add(new WordpoolWord(trimmed, lineNum));
                    } else {
                        words.add(new WordpoolWord(trimmed, -1));
                    }
                }
            }
        }
        return words;
    }
}
