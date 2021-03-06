/*
 *  Copyright (C) 2010 {Apertum}Projects. web: www.apertum.ru email: info@apertum.ru
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ru.apertum.qsystem.common;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import ru.apertum.qsystem.client.Locales;
import ru.apertum.qsystem.common.model.ATalkingClock;

/**
 * Компонент расширенной метки JLabel. Класс, расширяющий JLabel. Добавлены свойства создания бегущего текста, статического текста без привязки к лайаутам и
 * т.д. Умеет мигать.
 *
 * @author Evgeniy Egorov
 */
public class RunningLabel extends JLabel implements Serializable {

    public RunningLabel() {

        addComponentListener(new java.awt.event.ComponentAdapter() {

            @Override
            public void componentResized(java.awt.event.ComponentEvent evt) {
                onResize(evt);
            }
        });
    }

    /**
     * Событие ресайза метки. Если что-то нужно повесиь на ресайз, то перекрыть этод метод, незабыв вызвать предка.
     *
     * @param evt
     */
    protected void onResize(java.awt.event.ComponentEvent evt) {
        needRepaint();
    }
    /**
     * Событие перерисовки 25 кадров в секунду.
     */
    private final ActionListener actionListener = (ActionEvent e) -> {
        need = true;
        repaint();
    };
    private final PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        super.addPropertyChangeListener(listener);
        if (listener == null || propertySupport == null) {
            return;
        }
        propertySupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        super.removePropertyChangeListener(listener);
        if (listener == null || propertySupport == null) {
            return;
        }
        propertySupport.removePropertyChangeListener(listener);
    }

    @Override
    public void setVerticalAlignment(int alignment) {
        super.setVerticalAlignment(alignment);
        needRepaint();
    }

    @Override
    public void setHorizontalAlignment(int alignment) {
        super.setHorizontalAlignment(alignment);
        needRepaint();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        delta = 100 + 1 * font.getSize();
        needRepaint();
    }
    private int delta = 100;
    private int nPosition = 0;
    private int nTitleHeight;
    private Image mImg;
    private Graphics gImg = null;
    private Dimension dmImg = null;
    public static final String PROP_SPEED = "speedRunningText";
    /**
     * Скорость движения текста. На сколько пикселей сместится кадр относительно предыдущего при 24 кадра в секкунду. По умолчанию 10 пикселей, т.е. 240
     * пикселей с секунку будет скорость движения текста.
     */
    private int speedRunningText = 10;

    public int getSpeedRunningText() {
        return speedRunningText;
    }

    public void setSpeedRunningText(int speedRunningText) {
        final int oldValue = speedRunningText;
        this.speedRunningText = speedRunningText;
        propertySupport.firePropertyChange(PROP_SPEED, oldValue, speedRunningText);
    }
    public static final String PROP_RUNNING_TEXT = "running_text";
    /**
     * Бегущий текст, это не тот же что статический
     */
    private String runningText = "runningText";
    private int[] sizes = new int[0];
    private int totalSize = 0;

    private void setSizes(String text) {
        // подсчитаем длины
        sizes = new int[text.length()];
        totalSize = getFontMetrics(getFont()).stringWidth(text);
        for (int i = 0; i < text.length(); i++) {
            sizes[i] = getFontMetrics(getFont()).stringWidth(text.substring(i, i + 1));
            //System.out.print(" " + sizes[i]);
        }
        //System.out.println();
        //System.out.println(getFont().getName() + " " + totalSize + " " + text);
    }

    /**
     * Этот метод установит новое значение бегущего текста, выведет его на конву и отцентрирует его в зависимости от установленных выравниваний.
     *
     * @param text устанавливаемый бегущий текст
     */
    public void setRunningText(String text) {
        if (new File(text).exists()) {
            prepareStrings(text);
            currentLine = 0;
        } else {
            currentLine = -1;
            lines.clear();
        }
        final String oldValue = runningText;
        this.runningText = text;
        propertySupport.firePropertyChange(PROP_RUNNING_TEXT, oldValue, runningText);
        //setSizes(getRunningText());
        needRepaint();
    }
    private String oldTxt = "";

    public String getRunningText() {
        final String txt = isShowTime() ? getDate() : (currentLine >= 0 && !lines.isEmpty() ? getNextStringFromLines() : runningText);
        if (sizes == null || oldTxt.length() != txt.length()) {
            setSizes(txt);
        }
        oldTxt = txt;
        return txt;
    }
    private final LinkedList<String> lines = new LinkedList<>();
    private String fileWithStrings;
    private boolean needNextLine = false;
    private int currentLine = -1;//то что надо выводить

    private void prepareStrings(String filePath) {
        fileWithStrings = filePath;
        InputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
        } catch (FileNotFoundException ex) {
        }
        try {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("utf8")))) {
                String line;
                lines.clear();
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("#")) {
                        lines.add(line.trim());
                    }
                    //System.out.println(line);
                }
            }
        } catch (IOException ex) {
            QLog.l().logger().error("Cant read running strings from file " + filePath, ex);
        }
        // XSLT
        if (lines.size() == 3 && lines.getFirst().toLowerCase().startsWith("xml")) {
            String url = lines.get(1);

            final Pattern pattern = Pattern.compile("##.+?##");
            final Matcher matcher = pattern.matcher(url);
            // check all occurance
            while (matcher.find()) {
                final SimpleDateFormat sdf2 = new SimpleDateFormat(matcher.group().substring(2, (matcher.group().length() - 2)));
                url = url.replace(matcher.group(), sdf2.format(new Date()));
            }
            try {
                final URL xmlIn = new URL(url);
                final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder builder = factory.newDocumentBuilder();
                final Document document = builder.parse(xmlIn.openStream());

                // Use a Transformer for output
                final URL xslIn = new URL(lines.get(2));
                final StreamSource stylesource = new StreamSource(xslIn.openStream());

                final TransformerFactory tFactory = TransformerFactory.newInstance();
                final Transformer transformer = tFactory.newTransformer(stylesource);

                final DOMSource source = new DOMSource(document);
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                final StreamResult result = new StreamResult(os);
                transformer.transform(source, result);

                fis = new ByteArrayInputStream(os.toByteArray());
                try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("utf8")))) {
                    String line;
                    lines.clear();
                    while ((line = br.readLine()) != null) {
                        if (!line.startsWith("#")) {
                            lines.add(line.trim());
                        }
                        //System.out.println(line);
                    }
                }
            } catch (IOException ex) {
                QLog.l().logger().error("Cant read running strings from XSLT 1 " + filePath, ex);
            } catch (ParserConfigurationException | SAXException ex) {
                QLog.l().logger().error("Cant read running strings from XSLT 2 " + filePath, ex);
            } catch (TransformerConfigurationException ex) {
                QLog.l().logger().error("Cant read running strings from XSLT 3 " + filePath, ex);
            } catch (TransformerException ex) {
                QLog.l().logger().error("Cant read running strings from XSLT 4 " + filePath, ex);
            }
        }
    }

    private String getNextStringFromLines() {
        final String res;
        if (needNextLine) {
            prepareStrings(fileWithStrings);
            if (lines.size() == 0) {
                res = runningText;// файл пустой подсунули
            } else {
                if (currentLine < lines.size()) {
                    res = lines.get(currentLine);
                    currentLine = currentLine == lines.size() - 1 ? 0 : currentLine + 1;
                } else {
                    res = lines.get(0);
                    currentLine = 1;
                }
            }
            sizes = null;// переразбить длины
        } else {
            currentLine = currentLine > lines.size() - 1 ? 0 : currentLine;
            res = lines.get(currentLine);
        }
        needNextLine = false;
        return res;
    }
    private SimpleDateFormat sdf;

    private String getDate() {
        if (Locales.getInstance().isRuss) {
            if (sdf == null) {
                DateFormatSymbols russSymbol = new DateFormatSymbols(Locales.getInstance().getLangCurrent());
                russSymbol.setMonths(Uses.RUSSIAN_MONAT);
                sdf = new SimpleDateFormat("dd MMMM  HH.mm:ss", russSymbol);
            }
            return sdf.format(new Date());
        } else {
            return Uses.format_for_label2.format(new Date());
        }
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        needRepaint();
    }

    @Override
    public void setForeground(Color fg) {
        super.setForeground(fg);
        needRepaint();
    }

    private void needRepaint() {
        needRepaint = true;
        //repaint();
    }

    @Override
    public void paint(Graphics g) {
        if (backgroundImg != null || !"".equals(getRunningText()) || isShowTime()) {
            // Тут условия на изменение картинки с текстом
            if (((run || isBlink()) && need) || ((run == false && isBlink() == false) && need) || needRepaint) {

                updateImg();
                need = false;
                needRepaint = false;
            }
            if (mImg != null) {
                g.drawImage(mImg, 0, 0, null);
            } else {
                if (backgroundImg != null) {
                    g.drawImage(backgroundImg, 0, 0, null);
                }
            }
        }
        super.paint(g);
    }

    @Override
    public void update(Graphics g) {
        // не знаю зачем, попытка улучшить прорисовку, но не проверено как повлияло  paint(getGraphics());
    }

    /**
     * Формирование картинки сдвинутой для эффекта смещения
     */
    private void updateImg() {

        Dimension dm = getSize();
        int wndWidth = dm.width;
        int wndHeight = dm.height;

        if ((dmImg == null)
                || (dmImg.width != wndWidth)
                || (dmImg.height != wndHeight)) {
            dmImg = new Dimension(wndWidth, wndHeight);
            mImg = createImage(wndWidth, wndHeight);
            gImg = mImg.getGraphics();
        }

        Color fg = getForeground();
        Color bg = getBackground();
        gImg.setColor(bg);

        //Теперь мы закрашиваем изображение:
        gImg.fillRect(0, 0, dmImg.width, dmImg.height);
        if (getBackgroundImage() != null) {
            gImg.drawImage(backgroundImg, 0, 0, null);
        }
        gImg.setColor(fg);

        // отцентрируем по вертикале
        final int y;
        switch (getVerticalAlignment()) {
            case SwingConstants.CENTER:
                y = (getHeight() - (int) (getFont().getSize() * 0.95)) / 2;
                break;
            case SwingConstants.BOTTOM:
                y = getHeight() - (int) (getFont().getSize() * 0.95);
                break;
            default:
                y = 0;
        }
        /*
         CENTER  = 0;
         TOP     = 1;
         LEFT    = 2;
         BOTTOM  = 3;
         RIGHT   = 4;
         */
        // отцентрируем по горизонтале
        final int len = sizes.length == 0 ? 0 : totalSize;
        if (!run) {
            switch (getHorizontalAlignment()) {
                case SwingConstants.CENTER:
                    nPosition = (getWidth() - len) / 2;
                    break;
                case SwingConstants.RIGHT:
                    nPosition = getWidth() - len;
                    break;
                default:
                    nPosition = 0;
            }
        }
        //Затем рисуем строку в контексте изображения:
        gImg.setFont(getFont());

        String forDrow = null;
        nPosition = nPosition - (run ? getSpeedRunningText() : 0);
        if (nPosition < -len) { // проверка, если уехало целиков за левый край, то перекинем все за правый край
            needNextLine = true; // повторный пробег
            forDrow = getRunningText(); // подсосалась и обработалась следующая строка
            nPosition = getSize().width;
        }
        if (forDrow == null) {// уже подучили в предыдущем IF
            forDrow = getRunningText();
        }
        if (isVisibleRunningText && !forDrow.isEmpty()) {
            int lenHead = 0;
            int pos = -1;// позиция последней отрубаемой буквы
            while (-nPosition - delta > lenHead) {
                lenHead = lenHead + sizes[++pos];
            }

            int posLast = pos == -1 ? 0 : pos;
            int lenTail = 0;
            while (++posLast < forDrow.length() && getWidth() + delta > lenTail) {
                lenTail = lenTail + sizes[posLast];
            }
            forDrow = forDrow.substring(pos == -1 ? 0 : pos + 1, posLast);
            //System.out.println("st=" + pos + " fin=" + posLast + "     x=" + (nPosition + lenHead) + " y=" + (y + nTitleHeight + (int) (getFont().getSize() * 0.75)) + " : " + forDrow);
            gImg.drawString(forDrow,
                    nPosition + lenHead,
                    y + nTitleHeight + (int) (getFont().getSize() * 0.75));
        }
    }
    public static final String PROP_BACKGROUND_IMG = "backgroundImgage";
    /**
     * Фоновая картинка.
     */
    private String backgroundImage = "";
    private Image backgroundImg = null;

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String resourceName) {
        backgroundImage = resourceName;
        final String oldValue = resourceName;
        this.backgroundImg = Uses.loadImage(this, resourceName, "");
        propertySupport.firePropertyChange(PROP_BACKGROUND_IMG, oldValue, resourceName);
        needRepaint();
    }
    public static final String PROP_IS_RUN = "isRunText";
    /**
     * бежит ли строка
     */
    private Boolean run = false;

    public Boolean isRun() {
        return run;
    }

    public void setRun(Boolean run) {
        final Boolean oldValue = run;
        this.run = run;
        propertySupport.firePropertyChange(PROP_IS_RUN, oldValue, run);
        if (run) {
            start();
        } else {
            stop();
        }
    }
    /**
     * Возникло ли событие смещение надписи
     */
    private boolean need = false;
    private boolean needRepaint = false;
    /**
     * Поток генерации событий смещения текста
     */
    //private Thread timerThread = null;
    private final Timer timerThread = new Timer(40, actionListener);

    /**
     * Запустить бегущий текст
     */
    public void start() {
        run = true;
        setRateMainTimer();
        /**
         * Создать поток для инициализации перересовки. Он выступает так же и в качестве таймера для перересовки.
         */
        nPosition = getWidth();
        if (!timerThread.isRunning()) {
            timerThread.start();
        }
    }

    /**
     * Остановить бегущий текст
     */
    public void stop() {
        run = false;
        setRateMainTimer();
        if (!isBlink() && !showTime) {
            timerThread.stop();
        }
    }
    public static final String PROP_BLINK_COUNT = "blinkCount";
    /**
     * Количество миганий текста.
     */
    private int blinkCount = 0;

    public int getBlinkCount() {
        return blinkCount;
    }

    public void setBlinkCount(int blinkCount) {
        final int oldValue = blinkCount;
        this.blinkCount = blinkCount;
        propertySupport.firePropertyChange(PROP_BLINK_COUNT, oldValue, blinkCount);
        blinkTimer.setCount(blinkCount * 2);
    }
    public static final String PROP_SPEED_BLINK = "speed_blink";
    /**
     * Скорость миганий текста.
     */
    private int speedBlink = 500;

    public int getSpeedBlink() {
        return speedBlink;
    }

    public void setSpeedBlink(int speedBlink) {
        final int oldValue = blinkCount;
        this.speedBlink = speedBlink;
        propertySupport.firePropertyChange(PROP_SPEED_BLINK, oldValue, speedBlink);
        blinkTimer.setInterval(speedBlink);
    }
    /**
     * Таймер мигания надписи
     */
    private final ATalkingClock blinkTimer = new ATalkingClock(getSpeedBlink(), getBlinkCount()) {

        @Override
        public void run() {
            setRateMainTimer();
            isVisibleRunningText = !isVisibleRunningText;
        }

        @Override
        public void stop() {
            super.stop();
            stopBlink();
        }
    };

    /**
     * Запустить мигание текста
     */
    public void startBlink() {
        if (!blinkTimer.isActive()) {
            blinkTimer.start();
        }

        /**
         * Создать поток для инициализации перересовки. Он выступает так же и в качестве таймера для перересовки.
         */
        if (!timerThread.isRunning()) {
            timerThread.start();
        }
    }

    /**
     * Остановить мигание текста
     */
    public void stopBlink() {
        if (blinkTimer.isActive()) {
            blinkTimer.stop();
        }
        isVisibleRunningText = true;
        if (!run && !showTime) {
            need = true;
            repaint();
        }
        setRateMainTimer();
    }
    /**
     * Нужно для мигания.
     */
    private boolean isVisibleRunningText = true;

    /**
     * мигает ли строка
     */
    private boolean isBlink() {
        return blinkTimer.isActive();
    }
    public static final String PROP_SHOW_TIME = "showTime";
    /**
     * Показывать или нет время вместо текста на бегущей строке.
     */
    private Boolean showTime = false;

    public Boolean isShowTime() {
        return showTime;
    }

    public void setShowTime(Boolean showTime) {
        final Boolean oldValue = showTime;
        this.showTime = showTime;
        propertySupport.firePropertyChange(PROP_SHOW_TIME, oldValue, showTime);
        setRateMainTimer();
        /**
         * Создать поток для инициализации перересовки. Он выступает так же и в качестве таймера для перересовки.
         */
        if (showTime) {
            setSizes(getDate());
            timerThread.start();
        } else {
            setRunningText(runningText);
            needRepaint();
        }
    }

    private void setRateMainTimer() {
        timerThread.setDelay(run ? 40 : (isBlink() ? getSpeedBlink() : 1000));
        if (timerThread.isRunning()) {
            timerThread.restart();
        }
    }
}
