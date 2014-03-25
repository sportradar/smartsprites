package org.carrot2.labs.smartsprites;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.carrot2.labs.smartsprites.css.CssProperty;
import org.carrot2.labs.smartsprites.css.CssSyntaxUtils;
import org.carrot2.labs.smartsprites.message.Message.MessageType;
import org.carrot2.labs.smartsprites.message.MessageLog;
import org.carrot2.labs.smartsprites.resource.ResourceHandler;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.io.Closeables;

/**
 * Methods for collecting SmartSprites directives from CSS files.
 */
public class SpriteDirectiveOccurrenceCollector
{
    /** A regular expression for extracting sprite image directives */
    private final static Pattern SPRITE_IMAGE_DIRECTIVE = Pattern
        .compile("/\\*+\\s+(sprite:[^*]*)\\*+/");

    /** A regular expression for extracting sprite reference directives */
    private final static Pattern SPRITE_REFERENCE_DIRECTIVE = Pattern
        .compile("/\\*+\\s+(sprite-ref:[^*]*)\\*+/");

    /** This builder's message log */
    private final MessageLog messageLog;

    /** The resource handler */
    private final ResourceHandler resourceHandler;

    /**
     * Creates a {@link SpriteDirectiveOccurrenceCollector} with the provided parameters
     * and log.
     */
    SpriteDirectiveOccurrenceCollector(MessageLog messageLog,
        ResourceHandler resourceHandler)
    {
        this.resourceHandler = resourceHandler;
        this.messageLog = messageLog;
    }

    /**
     * Collects {@link SpriteImageOccurrence}s from a single CSS file.
     */
    Collection<SpriteImageOccurrence> collectSpriteImageOccurrences(String cssFile)
        throws FileNotFoundException, IOException
    {
        final Collection<SpriteImageOccurrence> occurrences = Lists.newArrayList();
        final BufferedReader reader = new BufferedReader(resourceHandler
            .getResourceAsReader(cssFile));
        messageLog.setCssFile(null);
        messageLog.info(MessageType.READING_SPRITE_IMAGE_DIRECTIVES, cssFile);
        messageLog.setCssFile(cssFile);

        int lineNumber = -1;
        String line;

        try
        {
            while ((line = reader.readLine()) != null)
            {
                messageLog.setLine(++lineNumber);

                final String spriteImageDirectiveString = extractSpriteImageDirectiveString(line);
                if (spriteImageDirectiveString == null)
                {
                    continue;
                }

                final SpriteImageDirective directive = SpriteImageDirective.parse(
                    spriteImageDirectiveString, messageLog);
                if (directive == null)
                {
                    continue;
                }

                occurrences
                    .add(new SpriteImageOccurrence(directive, cssFile, lineNumber));
            }
        }
        finally
        {
            Closeables.closeQuietly(reader);
        }

        return occurrences;
    }

    /**
     * Collects {@link SpriteReferenceOccurrence}s from a single CSS file.
     */
    Collection<SpriteReferenceOccurrence> collectSpriteReferenceOccurrences(
        String cssFile, Map<String, SpriteImageDirective> spriteImageDirectives)
        throws FileNotFoundException, IOException
    {
        final Collection<SpriteReferenceOccurrence> directives = Lists.newArrayList();

        final BufferedReader reader = new BufferedReader(resourceHandler
            .getResourceAsReader(cssFile));
        messageLog.setCssFile(null);
        messageLog.info(MessageType.READING_SPRITE_REFERENCE_DIRECTIVES, cssFile);
        messageLog.setCssFile(cssFile);

        int lineNumber = -1, effectiveLineNumber;
        String line, prevLine = null;

        final boolean checkForMultiline = true;

        try
        {
            while ((line = reader.readLine()) != null)
            {
                messageLog.setLine(++lineNumber);
                effectiveLineNumber = lineNumber;

                final String directiveString = extractSpriteReferenceDirectiveString(line);
                if (directiveString == null)
                {
                    prevLine = line;
                    continue;
                }

                ExtractorResult res = extractSpriteReferenceCssProperty(line, checkForMultiline ? prevLine : null);
                if (res == null) {
                    continue;
                }

                CssProperty backgroundProperty = res.cssProperty;

                final String imageUrl = CssSyntaxUtils.unpackUrl(
                    backgroundProperty.value, messageLog);
                if (imageUrl == null)
                {
                    prevLine = line;
                    continue;
                }

                final SpriteReferenceDirective directive = SpriteReferenceDirective
                    .parse(directiveString, spriteImageDirectives, messageLog);
                if (directive == null)
                {
                    prevLine = line;
                    continue;
                }

                if (res.propertyInPrevLine) {
                    effectiveLineNumber--;
                }

                directives.add(new SpriteReferenceOccurrence(directive, imageUrl,
                    cssFile, effectiveLineNumber, backgroundProperty.important, res.propertyInPrevLine));

                prevLine = line;
            }
        }
        finally
        {
            Closeables.closeQuietly(reader);
        }

        return directives;
    }

    /**
     * Collects {@link SpriteImageOccurrence}s from the provided CSS files.
     */
    Multimap<String, SpriteImageOccurrence> collectSpriteImageOccurrences(
        Collection<String> filePaths) throws FileNotFoundException, IOException
    {
        final Multimap<String, SpriteImageOccurrence> spriteImageOccurrencesByFile = LinkedListMultimap
            .create();
        for (final String cssFile : filePaths)
        {
            messageLog.setCssFile(cssFile);

            final Collection<SpriteImageOccurrence> spriteImageOccurrences = collectSpriteImageOccurrences(cssFile);

            spriteImageOccurrencesByFile.putAll(cssFile, spriteImageOccurrences);
        }
        return spriteImageOccurrencesByFile;
    }

    /**
     * Collects {@link SpriteReferenceOccurrence}s from the provided CSS files.
     */
    Multimap<String, SpriteReferenceOccurrence> collectSpriteReferenceOccurrences(
        Collection<String> files,
        final Map<String, SpriteImageDirective> spriteImageDirectivesBySpriteId)
        throws FileNotFoundException, IOException
    {
        final Multimap<String, SpriteReferenceOccurrence> spriteEntriesByFile = LinkedListMultimap
            .create();
        for (final String cssFile : files)
        {
            messageLog.setCssFile(cssFile);

            final Collection<SpriteReferenceOccurrence> spriteReferenceOccurrences = collectSpriteReferenceOccurrences(
                cssFile, spriteImageDirectivesBySpriteId);

            spriteEntriesByFile.putAll(cssFile, spriteReferenceOccurrences);
        }
        return spriteEntriesByFile;
    }

    /**
     * Groups {@link SpriteImageDirective}s by sprite id.
     */
    Map<String, SpriteImageOccurrence> mergeSpriteImageOccurrences(
        final Multimap<String, SpriteImageOccurrence> spriteImageOccurrencesByFile)
    {
        final Map<String, SpriteImageOccurrence> spriteImageDirectivesBySpriteId = Maps
            .newLinkedHashMap();
        for (final Map.Entry<String, SpriteImageOccurrence> entry : spriteImageOccurrencesByFile
            .entries())
        {
            final String cssFile = entry.getKey();
            final SpriteImageOccurrence spriteImageOccurrence = entry.getValue();

            messageLog.setCssFile(cssFile);

            // Add to the global map, checking for duplicates
            if (spriteImageDirectivesBySpriteId
                .containsKey(spriteImageOccurrence.spriteImageDirective.spriteId))
            {
                messageLog.warning(MessageType.IGNORING_SPRITE_IMAGE_REDEFINITION);
            }
            else
            {
                spriteImageDirectivesBySpriteId.put(
                    spriteImageOccurrence.spriteImageDirective.spriteId,
                    spriteImageOccurrence);
            }
        }
        return spriteImageDirectivesBySpriteId;
    }

    /**
     * Groups {@link SpriteReferenceOccurrence}s by sprite id.
     */
    static Multimap<String, SpriteReferenceOccurrence> mergeSpriteReferenceOccurrences(
        final Multimap<String, SpriteReferenceOccurrence> spriteEntriesByFile)
    {
        final Multimap<String, SpriteReferenceOccurrence> spriteReferenceOccurrencesBySpriteId = LinkedListMultimap
            .create();
        for (final SpriteReferenceOccurrence spriteReferenceOccurrence : spriteEntriesByFile
            .values())
        {
            spriteReferenceOccurrencesBySpriteId.put(
                spriteReferenceOccurrence.spriteReferenceDirective.spriteRef,
                spriteReferenceOccurrence);
        }
        return spriteReferenceOccurrencesBySpriteId;
    }

    /**
     * Extract the sprite image directive string to be parsed.
     */
    static String extractSpriteImageDirectiveString(String cssLine)
    {
        final Matcher matcher = SPRITE_IMAGE_DIRECTIVE.matcher(cssLine);

        if (matcher.find())
        {
            return matcher.group(1).trim();
        }
        else
        {
            return null;
        }
    }

    /**
     * Extract the sprite reference directive string to be parsed.
     */
    static String extractSpriteReferenceDirectiveString(String css)
    {
        final Matcher matcher = SPRITE_REFERENCE_DIRECTIVE.matcher(css);

        if (matcher.find())
        {
            return matcher.group(1).trim();
        }
        else
        {
            return null;
        }
    }

    /**
     * Extract the url to the image to be added to a sprite.
     */
    CssProperty extractSpriteReferenceCssProperty(String css) {
        ExtractorResult res = extractSpriteReferenceCssProperty(css, null);

        if (res != null) {
            return res.cssProperty;
        }
        return null;
    }

    private class ExtractorResult {
        public CssProperty cssProperty;
        public boolean propertyInPrevLine;
    }

    /**
     * Extract the url to the image to be added to a sprite.
     */
    ExtractorResult extractSpriteReferenceCssProperty(String css, String prevLine)
    {
        Matcher matcher = SPRITE_REFERENCE_DIRECTIVE.matcher(css);

        // Remove the directive
        String noDirective = matcher.replaceAll("").trim();

        boolean propertyInPrevLine = false;

        Collection<CssProperty> rules = CssSyntaxUtils
            .extractProperties(noDirective);
        if (rules.size() == 0)
        {
            if (prevLine != null) {
                matcher = SPRITE_REFERENCE_DIRECTIVE.matcher(prevLine);

                // Remove the directive
                noDirective = matcher.replaceAll("").trim();
                rules = CssSyntaxUtils.extractProperties(noDirective);

                if (rules.size() == 0)
                {
                    messageLog.warning(
                            MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                            prevLine);
                    messageLog.warning(
                            MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                            css);
                    return null;
                }

                propertyInPrevLine = true;

            } else {
                messageLog.warning(
                        MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                        css);
                return null;
            }
        }

        if (rules.size() > 1)
        {
            messageLog.warning(
                MessageType.MORE_THAN_ONE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE, css);
            return null;
        }

        final CssProperty backgroundImageRule = rules.iterator().next();
        if (!backgroundImageRule.rule.equals("background-image"))
        {
            messageLog.warning(
                MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                css);
            return null;
        }

        ExtractorResult result = new ExtractorResult();
        result.cssProperty = backgroundImageRule;
        result.propertyInPrevLine = propertyInPrevLine;

        return result;
    }
}
