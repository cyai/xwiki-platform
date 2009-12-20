/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.officeimporter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.logging.Logger;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.model.DocumentNameFactory;
import org.xwiki.officeimporter.builder.PresentationBuilder;
import org.xwiki.officeimporter.builder.XDOMOfficeDocumentBuilder;
import org.xwiki.officeimporter.builder.XHTMLOfficeDocumentBuilder;
import org.xwiki.officeimporter.document.XDOMOfficeDocument;
import org.xwiki.officeimporter.document.XHTMLOfficeDocument;
import org.xwiki.officeimporter.openoffice.OpenOfficeManager;
import org.xwiki.officeimporter.openoffice.OpenOfficeManager.ManagerState;
import org.xwiki.officeimporter.splitter.TargetPageDescriptor;
import org.xwiki.officeimporter.splitter.XDOMOfficeDocumentSplitter;

/**
 * A bridge between velocity and office importer.
 * 
 * @version $Id: OfficeImporterVelocityBridge.java 24508 2009-10-15 10:05:22Z asiri $
 * @since 1.8M1
 */
public class OfficeImporterVelocityBridge
{
    /**
     * File extensions corresponding to slide presentations.
     */
    public static final List<String> PRESENTATION_FORMAT_EXTENSIONS = Arrays.asList("ppt", "pptx", "odp");

    /**
     * The key used to place any error messages while importing office documents.
     */
    public static final String OFFICE_IMPORTER_ERROR = "OFFICE_IMPORTER_ERROR";

    /**
     * The {@link Execution} component.
     */
    private Execution execution;

    /**
     * Internal {@link OfficeImporter} component.
     */
    private OfficeImporter importer;

    /**
     * The {@link DocumentAccessBridge} component.
     */
    private DocumentAccessBridge docBridge;

    /**
     * Used for converting string document names to {@link DocumentName} instances.
     */
    private DocumentNameFactory nameFactory;

    /**
     * Used to query openoffice server status.
     */
    private OpenOfficeManager officeManager;

    /**
     * Used for building {@link XHTMLOfficeDocument} instances from office documents.
     */
    private XHTMLOfficeDocumentBuilder xhtmlBuilder;

    /**
     * Used for building {@link XDOMOfficeDocument} instances from office documents.
     */
    private XDOMOfficeDocumentBuilder xdomBuilder;

    /**
     * Used for building {@link XDOMOfficeDocument} instances from office presentations.
     */
    private PresentationBuilder presentationBuilder;

    /**
     * Used to split {@link XDOMOfficeDocument} documents.
     */
    private XDOMOfficeDocumentSplitter xdomSplitter;

    /**
     * The {@link Logger} instance.
     */
    private Logger logger;

    /**
     * Default constructor.
     * 
     * @param componentManager used to lookup for other necessary components.
     * @param logger logger.
     * @throws OfficeImporterException if an error occurs while looking up for other required components.
     */
    public OfficeImporterVelocityBridge(ComponentManager componentManager, Logger logger)
        throws OfficeImporterException
    {
        this.logger = logger;
        try {
            this.execution = componentManager.lookup(Execution.class);
            this.importer = componentManager.lookup(OfficeImporter.class);
            this.docBridge = componentManager.lookup(DocumentAccessBridge.class);
            this.nameFactory = componentManager.lookup(DocumentNameFactory.class);
            this.officeManager = componentManager.lookup(OpenOfficeManager.class);
            this.xhtmlBuilder = componentManager.lookup(XHTMLOfficeDocumentBuilder.class);
            this.xdomBuilder = componentManager.lookup(XDOMOfficeDocumentBuilder.class);
            this.presentationBuilder = componentManager.lookup(PresentationBuilder.class);
            this.xdomSplitter = componentManager.lookup(XDOMOfficeDocumentSplitter.class);
        } catch (Exception ex) {
            throw new OfficeImporterException("Error while initializing office importer velocity bridge.", ex);
        }
    }

    /**
     * <p>
     * Imports the given office document into an {@link XHTMLOfficeDocument}.
     * </p>
     * 
     * @param officeFileStream binary data stream corresponding to input office document.
     * @param officeFileName name of the input office document, this argument is mainly used for determining input
     *            document format where necessary.
     * @param referenceDocument reference wiki document w.r.t which import process is carried out. This argument affects
     *            the attachment URLs generated during the import process where all references to attachments will be
     *            calculated assuming that the attachments are contained on the reference document.
     * @param filterStyles whether to filter styling information associated with the office document's content or not.
     * @return {@link XHTMLOfficeDocument} containing xhtml result of the import operation or null if an error occurs.
     * @since 2.2M1
     */
    public XHTMLOfficeDocument officeToXHTML(InputStream officeFileStream, String officeFileName,
        String referenceDocument, boolean filterStyles)
    {
        try {
            connect();
            return xhtmlBuilder.build(officeFileStream, officeFileName, nameFactory
                .createDocumentName(referenceDocument), filterStyles);
        } catch (OfficeImporterException ex) {
            setErrorMessage(ex.getMessage());
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * <p>
     * Imports the given {@link XHTMLOfficeDocument} into an {@link XDOMOfficeDocument}.
     * </p>
     * 
     * @param xhtmlOfficeDocument {@link XHTMLOfficeDocument} to be imported.
     * @return {@link XDOMOfficeDocument} containing {@link XDOM} result of the import operation or null if an error
     *         occurs.
     * @since 2.2M1
     */
    public XDOMOfficeDocument xhtmlToXDOM(XHTMLOfficeDocument xhtmlOfficeDocument)
    {
        try {
            return xdomBuilder.build(xhtmlOfficeDocument);
        } catch (OfficeImporterException ex) {
            setErrorMessage(ex.getMessage());
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * <p>
     * Imports the given office document into an {@link XDOMOfficeDocument}.
     * </p>
     * 
     * @param officeFileStream binary data stream corresponding to input office document.
     * @param officeFileName name of the input office document, this argument is mainly is used for determining input
     *            document format where necessary.
     * @param referenceDocument reference wiki document w.r.t which import process is carried out. This srgument affects
     *            the attachment URLs generated during the import process where all references to attachments will be
     *            calculated assuming that the attachments are contained on the reference document.
     * @param filterStyles whether to filter styling information associated with the office document's content or not.
     * @return {@link XDOMOfficeDocument} containing {@link XDOM} result of the import operation or null if an error
     *         occurs.
     * @since 2.2M1
     */
    public XDOMOfficeDocument officeToXDOM(InputStream officeFileStream, String officeFileName,
        String referenceDocument, boolean filterStyles)
    {
        try {
            connect();
            if (isPresentation(officeFileName)) {
                return presentationBuilder.build(officeFileStream, officeFileName);
            } else {
                return xdomBuilder.build(officeFileStream, officeFileName, nameFactory
                    .createDocumentName(referenceDocument), filterStyles);
            }
        } catch (OfficeImporterException ex) {
            setErrorMessage(ex.getMessage());
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * <p>
     * Splits the given {@link XDOMOfficeDocument} into multiple {@link XDOMOfficeDocument} instances according to the
     * specified criterion. This method is useful when a single office document has to be imported and split into
     * multiple wiki pages. An auto generated TOC structure will be returned associated to <b>rootDocumentName</b>
     * {@link TargetPageDescriptor} entry.
     * </p>
     * 
     * @param xdomDocument {@link XDOMOfficeDocument} to be split.
     * @param headingLevelsToSplit heading levels defining the split points on the original document.
     * @param namingCriterionHint hint indicating the child pages naming criterion.
     * @param rootDocumentName name of the root document w.r.t which splitting will occur. In the results set the entry
     *            corresponding to <b>rootDocumentName</b> {@link TargetPageDescriptor} will hold an auto-generated TOC
     *            structure.
     * @return a map holding {@link XDOMOfficeDocument} fragments against corresponding {@link TargetPageDescriptor}
     *         instances or null if an error occurs.
     * @since 2.2M1
     */
    public Map<TargetPageDescriptor, XDOMOfficeDocument> split(XDOMOfficeDocument xdomDocument,
        int[] headingLevelsToSplit, String namingCriterionHint, String rootDocumentName)
    {
        try {
            return xdomSplitter.split(xdomDocument, headingLevelsToSplit, namingCriterionHint, nameFactory
                .createDocumentName(rootDocumentName));
        } catch (OfficeImporterException ex) {
            setErrorMessage(ex.getMessage());
            logger.error(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * @return an error message set inside current execution (during import process) or null.
     */
    public String getErrorMessage()
    {
        return (String) execution.getContext().getProperty(OFFICE_IMPORTER_ERROR);
    }

    /**
     * Utility method for setting an error message inside current execution.
     * 
     * @param message error message.
     */
    private void setErrorMessage(String message)
    {
        execution.getContext().setProperty(OFFICE_IMPORTER_ERROR, message);
    }

    /**
     * Attempts to connect to an openoffice server for conversions.
     * 
     * @throws OfficeImporterException if an openoffice server is not available.
     */
    private void connect() throws OfficeImporterException
    {
        if (!officeManager.getState().equals(ManagerState.CONNECTED)) {
            throw new OfficeImporterException("OpenOffice server unavailable.");
        }
    }

    /**
     * Utility method for checking if a file name corresponds to an office presentation.
     * 
     * @param officeFileName office file name.
     * @return true if the file name / extension represents an office presentation format.
     */
    private boolean isPresentation(String officeFileName)
    {
        String extension = officeFileName.substring(officeFileName.lastIndexOf('.') + 1);
        return PRESENTATION_FORMAT_EXTENSIONS.contains(extension);
    }

    /**
     * Imports the passed Office document into the target wiki page.
     * 
     * @param fileContent the binary content of the input document.
     * @param fileName the name of the source document (should have a valid extension since the extension is used to
     *            find out the office document's format).
     * @param targetDocument the name of the resulting wiki page.
     * @param options the optional parameters for the conversion.
     * @return true if the operation was a success.
     * @deprecated use individual import methods instead since 2.2M1
     */
    @Deprecated
    public boolean importDocument(byte[] fileContent, String fileName, String targetDocument,
        Map<String, String> options)
    {
        boolean success = false;
        try {
            validateRequest(targetDocument, options);
            importer.importStream(new ByteArrayInputStream(fileContent), fileName, targetDocument, options);
            success = true;
        } catch (OfficeImporterException ex) {
            logger.error(ex.getMessage(), ex);
            execution.getContext().setProperty(OFFICE_IMPORTER_ERROR, ex.getMessage());
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            setErrorMessage("Internal error while finalizing the target document.");
        }
        return success;
    }

    /**
     * @return any error messages thrown while importing.
     * @deprecated use {@link #getErrorMessage()} instead since 2.2M1.
     */
    @Deprecated
    public String getLastErrorMessage()
    {
        return getErrorMessage();
    }

    /**
     * Checks if this request is valid. For a request to be valid, the target document should be editable by the current
     * user. And if this is not an append request, the target document should not exist.
     * 
     * @param targetDocument the target document.
     * @param options additional parameters passed in for the import operation.
     * @throws OfficeImporterException if the request is invalid.
     */
    private void validateRequest(String targetDocument, Map<String, String> options) throws OfficeImporterException
    {
        if (!docBridge.isDocumentEditable(nameFactory.createDocumentName(targetDocument))) {
            throw new OfficeImporterException("Inadequate privileges.");
        } else if (docBridge.exists(targetDocument) && !isAppendRequest(options)) {
            throw new OfficeImporterException("The target document " + targetDocument + " already exists.");
        }
    }

    /**
     * Utility method for checking if a request is made to append the importer result to an existing page.
     * 
     * @param options additional parameters passed in for the import operation.
     * @return true if the params indicate that this is an append request.
     */
    private boolean isAppendRequest(Map<String, String> options)
    {
        String appendParam = options.get("appendContent");
        return (appendParam != null) ? appendParam.equals("true") : false;
    }
}
