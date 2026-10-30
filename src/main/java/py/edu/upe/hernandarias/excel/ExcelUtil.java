package py.edu.upe.hernandarias.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Formato común de los Excel del sistema: título, encabezado azul, filtros y columnas ajustadas. */
public final class ExcelUtil {

    /** Las tablas empiezan en esta fila (0 = título, 1 = subtítulo, 2 = encabezado). */
    public static final int FILA_ENCABEZADO = 2;
    public static final int PRIMERA_FILA_DATOS = 3;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final byte[] AZUL = {(byte) 0x1e, (byte) 0x3a, (byte) 0x8a};
    private static final byte[] GRIS = {(byte) 0xf1, (byte) 0xf5, (byte) 0xf9};

    private ExcelUtil() {
    }

    /** Estilos de un libro (los estilos de POI pertenecen a un libro y no se comparten). */
    public record Estilos(CellStyle titulo, CellStyle subtitulo, CellStyle encabezado, CellStyle texto,
                          CellStyle existente, CellStyle destacado) {
    }

    public static Estilos estilos(XSSFWorkbook libro) {
        XSSFFont negrita = libro.createFont();
        negrita.setBold(true);

        XSSFFont fuenteTitulo = libro.createFont();
        fuenteTitulo.setBold(true);
        fuenteTitulo.setFontHeightInPoints((short) 15);
        CellStyle titulo = libro.createCellStyle();
        titulo.setFont(fuenteTitulo);

        XSSFFont fuenteSubtitulo = libro.createFont();
        fuenteSubtitulo.setItalic(true);
        fuenteSubtitulo.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle subtitulo = libro.createCellStyle();
        subtitulo.setFont(fuenteSubtitulo);

        XSSFFont fuenteEncabezado = libro.createFont();
        fuenteEncabezado.setBold(true);
        fuenteEncabezado.setColor(IndexedColors.WHITE.getIndex());
        XSSFCellStyle encabezado = libro.createCellStyle();
        encabezado.setFont(fuenteEncabezado);
        encabezado.setFillForegroundColor(new XSSFColor(AZUL, null));
        encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        encabezado.setVerticalAlignment(VerticalAlignment.CENTER);
        bordes(encabezado);

        CellStyle texto = libro.createCellStyle();
        bordes(texto);

        XSSFCellStyle existente = libro.createCellStyle();
        existente.setFillForegroundColor(new XSSFColor(GRIS, null));
        existente.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont fuenteGris = libro.createFont();
        fuenteGris.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        existente.setFont(fuenteGris);
        bordes(existente);

        CellStyle destacado = libro.createCellStyle();
        destacado.setFont(negrita);
        bordes(destacado);

        return new Estilos(titulo, subtitulo, encabezado, texto, existente, destacado);
    }

    private static void bordes(CellStyle estilo) {
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        estilo.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    /** Título, subtítulo y fila de encabezado de una hoja. */
    public static void encabezado(Sheet hoja, Estilos estilos, String titulo, String subtitulo, List<String> columnas) {
        celda(hoja, 0, 0, titulo, estilos.titulo());
        celda(hoja, 1, 0, subtitulo, estilos.subtitulo());
        if (columnas.size() > 1) {
            hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, columnas.size() - 1));
            hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, columnas.size() - 1));
        }
        Row fila = hoja.createRow(FILA_ENCABEZADO);
        fila.setHeightInPoints(20);
        for (int i = 0; i < columnas.size(); i++) {
            Cell celda = fila.createCell(i);
            celda.setCellValue(columnas.get(i));
            celda.setCellStyle(estilos.encabezado());
        }
        hoja.createFreezePane(0, PRIMERA_FILA_DATOS);
    }

    /** Escribe una fila: números como número, el resto como texto. */
    public static Row fila(Sheet hoja, int indice, List<?> valores, CellStyle estilo) {
        Row fila = hoja.createRow(indice);
        for (int i = 0; i < valores.size(); i++) {
            Object valor = valores.get(i);
            Cell celda = fila.createCell(i);
            if (valor instanceof Number numero) {
                celda.setCellValue(numero.doubleValue());
            } else if (valor != null) {
                celda.setCellValue(valor.toString());
            }
            celda.setCellStyle(estilo);
        }
        return fila;
    }

    /** Libro de una sola hoja con una tabla, listo para descargar. */
    public static byte[] tabla(String nombreHoja, String titulo, List<String> columnas, List<List<Object>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos estilos = estilos(libro);
            Sheet hoja = libro.createSheet(nombreHoja);
            encabezado(hoja, estilos, titulo, "Generado el " + LocalDate.now().format(FECHA), columnas);
            int indice = PRIMERA_FILA_DATOS;
            for (List<Object> valores : filas) {
                fila(hoja, indice++, valores, estilos.texto());
            }
            if (!filas.isEmpty()) {
                hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, indice - 1, 0, columnas.size() - 1));
            }
            ajustarColumnas(hoja, columnas.size());
            return aBytes(libro);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void ajustarColumnas(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) {
            hoja.autoSizeColumn(i);
            // margen extra y límites razonables (en 1/256 de carácter)
            hoja.setColumnWidth(i, Math.min(Math.max(hoja.getColumnWidth(i) + 3 * 256, 12 * 256), 60 * 256));
        }
    }

    public static void celda(Sheet hoja, int fila, int columna, String valor, CellStyle estilo) {
        Row row = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        Cell celda = row.createCell(columna);
        celda.setCellValue(valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FECHA);
    }

    public static byte[] aBytes(Workbook libro) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nombre de archivo sin caracteres problemáticos. */
    public static String nombreArchivo(String base) {
        return base.replaceAll("[^A-Za-z0-9._-]+", "-") + "-" + LocalDate.now() + ".xlsx";
    }
}
