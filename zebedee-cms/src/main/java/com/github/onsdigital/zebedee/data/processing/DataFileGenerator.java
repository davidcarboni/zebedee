package com.github.onsdigital.zebedee.data.processing;

import au.com.bytecode.opencsv.CSVWriter;

import com.github.onsdigital.zebedee.api.Content;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.DownloadSection;
import com.github.onsdigital.zebedee.data.json.TimeSerieses;
import com.github.onsdigital.zebedee.data.processing.xls.TimeSeriesCellWriter;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.model.ContentWriter;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.reader.Resource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.Header;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.github.onsdigital.logging.v2.event.SimpleEvent.info;

/**
 * Builds CSV, XLSX and potentially other files from a set of timeseries
 */
public class DataFileGenerator {
    private static final String DEFAULT_FILENAME = "data";

    ContentWriter contentWriter;
    ContentReader contentReader;

    static TimeSeriesCellWriter xlsCellWriter = TimeSeriesCellWriter.getInstance();

    // public DataFileGenerator(ContentWriter collectionWriter) {
    //     this.contentWriter = collectionWriter;
    // }

    public DataFileGenerator(ContentWriter collectionWriter, ContentReader contentReader) {
        this.contentWriter = collectionWriter;
        this.contentReader = contentReader;
    }

    /**
     * Generate the files for this data publication
     *
     * @param details  details about the publication to derive the save path
     * @param serieses the timeseries
     * @throws ZebedeeException 
     * @throws URISyntaxException 
     */
    public List<DownloadSection> generateDataDownloads(DataPublicationDetails details, TimeSerieses serieses) throws IOException, ZebedeeException, URISyntaxException {

        // turn our list of timeseries into a grid to write to file
        DataGrid grid = new DataGrid(serieses);

        // get the correct place to save data files
        String root = fileRoot(details);


        System.out.println("GENERATING DATA DOWNLOADS");
        // write the files
        List<DownloadSection> sections = new ArrayList<>();
        sections.add(writeCSV(grid, this.contentWriter, root + ".csv"));
        sections.add(writeXLS(grid, this.contentWriter, root + ".xlsx"));

        return sections;
    }

    private String fileRoot(DataPublicationDetails details) {
        if (details.landingPage.getDescription().getDatasetId() == null || details.landingPage.getDescription().getDatasetId().trim().length() == 0) {
            return details.datasetPage.getUri().toString() + "/" + DEFAULT_FILENAME;
        } else {
            return details.datasetPage.getUri().toString() + "/" + details.landingPage.getDescription().getDatasetId().toLowerCase().trim();
        }
    }

    /**
     * Write a DataGrid as an xlsx file
     *
     * @param dataGrid
     * @param contentWriter
     * @param xlsPath
     * @throws IOException
     * @throws ZebedeeException 
     * @throws URISyntaxException 
     */
    DownloadSection writeXLS(DataGrid dataGrid, ContentWriter contentWriter, String xlsPath) throws IOException, ZebedeeException, URISyntaxException {
        info().data("path", xlsPath).log("Writing XLS file from DataGrid.");
        try (
                Workbook wb = new SXSSFWorkbook(30);
                OutputStream stream = contentWriter.getOutputStream(xlsPath)
        ) {
            Sheet sheet = wb.createSheet("data");

            // Add the metadata
            for (DataGridRow dataGridRow : dataGrid.metadata) {
                xlsCellWriter.writeCells(dataGridRow, sheet.createRow(getNextRowIndex(sheet)));
            }

            // Write the data
            for (DataGridRow dataGridRow : dataGrid.rows) {
                xlsCellWriter.writeCells(dataGridRow, sheet.createRow(getNextRowIndex(sheet)));
            }
            wb.write(stream);

            info().data("path", xlsPath).log("Complete.");
     
        //   contentWriter.getOutputStream(xlsPath);
        //    Resource resource = contentReader.getResource(zipData.zipPath.toString());
        //     System.out.println(thisFile.getAbsolutePath());
        //     if (!thisFile.exists()){
        //         System.out.println("CANT FIND THE FILE");
        //         System.out.println(xlsPath);
        //     }
            return newDownloadSection("xlsx download", xlsPath);
        }
    }

    private int getNextRowIndex(Sheet sheet) {
        // Rows are indexed from 0. Getting the current number of rows is equal to last index + 1.
        return sheet.getPhysicalNumberOfRows();
    }

    /**
     * @param grid
     * @param contentWriter
     * @param csvPath
     */
    private DownloadSection writeCSV(DataGrid grid, ContentWriter contentWriter, String csvPath) throws IOException, BadRequestException {
        try (
                OutputStream outputStream = contentWriter.getOutputStream(csvPath);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, Charset.forName("UTF8"));
                CSVWriter writer = new CSVWriter(outputStreamWriter)
        ) {
            for (DataGridRow dataGridRow : grid.metadata) {
                String[] row = new String[dataGridRow.cells.size() + 1];
                row[0] = dataGridRow.label;
                for (int i = 1; i <= dataGridRow.cells.size(); i++)
                    row[i] = dataGridRow.cells.get(i - 1);
                writer.writeNext(row);
            }

            for (DataGridRow dataGridRow : grid.rows) {
                String[] row = new String[dataGridRow.cells.size() + 1];
                row[0] = dataGridRow.label;
                for (int i = 1; i <= dataGridRow.cells.size(); i++)
                    row[i] = dataGridRow.cells.get(i - 1);
                writer.writeNext(row);
            }
        }
        return newDownloadSection("csv download", csvPath);
    }

    private DownloadSection newDownloadSection(String title, String file) {
        DownloadSection section = new DownloadSection();
        String filename = Paths.get(file).getFileName().toString();

        section.setTitle(title);
        section.setFile(filename);
        return section;
    }
}
