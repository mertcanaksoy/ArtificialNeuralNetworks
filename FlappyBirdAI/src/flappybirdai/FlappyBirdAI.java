package flappybirdai;

import static flappybirdai.Pool.POPULATION;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

/** 
  Oyun başında kuşların popülasyonu 50 ile başlatılır.
  Oyun, kuşları boru konumuna göre girdi kuş pozisyonuna bakarak değerlendirir ve daha sonra
  kuşun çıktı sonucu zıplamasını ve koordinatları güncelleyip güncellememesini kontrol ederek
  güncelleme yapar ve daha sonra popülasyonun en iyi kuşunu bularak öğrenir 
 */
public class FlappyBirdAI extends JPanel implements Runnable{
    
    public static final Random rnd = new Random();

    //Ekran ölçüleri
    private static final int WIDTH = 576;           
    private static final int HEIGHT = 768;   

    //Kuş ölçüleri
    private static final int BIRD_WIDTH = 72;       
    private static final int BIRD_HEIGHT = 52;
    
    //Taban ölçüleri
    private static final int FLOOR_WIDTH = 672;
    private static final int FLOOR_HEIGHT = 224;
    private static final int FLOOR_OFFSET = 96;
    private static final int FLOOR_SPEED = 5;
    
    //Boru ölçüleri
    private static final int TUBE_WIDTH = 104;
    private static final int TUBE_HEIGHT = 640;
    private static final int TUBE_APERTURE = 250;

    //Kullanılacak sprite nesneleri
    private static BufferedImage   BACK_IMAGE;
    private static BufferedImage[] BIRD_IMAGES;
    private static BufferedImage   GROUND_IMAGE;
    private static BufferedImage   TUBE1_IMAGE;
    private static BufferedImage   TUBE2_IMAGE;
    
    //Oyun hızının değişmesi
    public static boolean speedUp;
    
    //kuş classı
    private static class Bird { 
        //Türleri bir anahtar olarak bağlayan Hashmap
        private static Map<Species, BufferedImage[]> cache = new WeakHashMap<Species, BufferedImage[]>();

        //Kuşun görüntüsü ve renk değişlenleri
        private static BufferedImage colorBird(final BufferedImage refImage,
                final Color color) {
            
            final BufferedImage image = new BufferedImage(BIRD_WIDTH,
                    BIRD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            
            final Color bright = color.brighter().brighter();
            final Color dark = color.darker().darker();
            
            for (int y = 0; y < BIRD_HEIGHT; ++y){
                for (int x = 0; x < BIRD_WIDTH; ++x) {
                    int argb = refImage.getRGB(x, y);
                    if (argb == 0xffe0802c)
                        argb = dark.getRGB();
                    else if (argb == 0xfffad78c)
                        argb = bright.getRGB();
                    else if (argb == 0xfff8b733)
                        argb = color.getRGB();
                    image.setRGB(x, y, argb);
                }
            }            
            return image;
        }
    
        //Her kuş için kullanılacak görüntü dizisi
        private BufferedImage[] images;
        
        private final Genome genome;    //Kuşun yapay sinir ağı
        private double height;          //kuş ne kadar yükseklikte
        private double velocity;        //kuşun dikeydeki hızı
        private double angle;           //Kuşun açısı
        private boolean flap;           //Zıplama durumu
        private int flaps;              //Zıplama sayısı
        private boolean dead;           //Kuşun yaşayıp yaşamama durumu

        //Constructor. Türler ve Sinir Ağını parametre olarak alıyor
        private Bird(final Species species, final Genome genome) {
            if (cache.containsKey(species))
                images = cache.get(species);
            else {
                final Color color = new Color(rnd.nextInt(0x1000000));
                images = new BufferedImage[3];
                for (int i = 0; i < 3; ++i)
                    images[i] = colorBird(BIRD_IMAGES[i], color);
                cache.put(species, images);
            }

            this.genome = genome;   
            height = HEIGHT / 2.0; 
        }
    }

    private static class Tube {
        
        //Borunun koordinatları
        private final double height;        
        private double position;
        //Kuşun tüpü geçip geçmediğini belirler
        private boolean passed;

        //Boru oluştuktan sonra ekranın sağında konumlanır
        private Tube(final int height) {
            this.height = height;
            position = WIDTH;
            passed = false;
        }
    }
   

    private static final int[]   XS     = new int[] { 2, 6, 14, 18, 26, 50, 54,
            58, 62, 66, 70, 70, 66, 62, 42, 22, 14, 10, 6, 2 };
    private static final int[]   YS     = new int[] { -34, -38, -42, -46, -50,
            -50, -46, -42, -38, -26, -22, -18, -10, -6, -2, -2, -6, -10, -18,
            -22 };
    private static final Polygon BOUNDS = new Polygon(XS, YS, XS.length);

    //Oyun için kullanılan görüntüleri okur
    static {
        try {
            BACK_IMAGE = upscale(ImageIO.read(new File("bg.png")));
            GROUND_IMAGE = upscale(ImageIO.read(new File("brick.png")));
            final BufferedImage birdImage = ImageIO.read(new File("bird.png"));
            
            //Kuşa kanat çırpma animasyonu verilmesi
            BIRD_IMAGES = new BufferedImage[] {
                    upscale(birdImage.getSubimage(0, 0, 36, 26)),
                    upscale(birdImage.getSubimage(36, 0, 36, 26)),
                    upscale(birdImage.getSubimage(72, 0, 36, 26)) };
            
            TUBE1_IMAGE = upscale(ImageIO.read(new File("tube1.png")));
            TUBE2_IMAGE = upscale(ImageIO.read(new File("tube2.png")));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    //Çarpışma için kullanılacak sınırları alır
    public static Dimension getBounds(final Graphics2D g, final Font font,
            final String text) {
        final int width = (int) font
                .getStringBounds(text, g.getFontRenderContext()).getWidth();
        final int height = (int) font
                .createGlyphVector(g.getFontRenderContext(), text)
                .getVisualBounds().getHeight();
        return new Dimension(width, height);
    }

    public static void main(final String[] args) {
        final JFrame frame = new JFrame();
        frame.addMouseListener(new CustomListener());
        frame.setResizable(false);
        frame.setTitle("Flappy Bird AI");
        frame.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        final FlappyBirdAI ai = new FlappyBirdAI();
        frame.add(ai);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        ai.run();
    }

    //Önbelleğe alınmış görüntüyü, görüntüye uygular
    private static BufferedImage toBufferedImage(final Image image) {
        final BufferedImage buffered = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        buffered.getGraphics().drawImage(image, 0, 0, null);
        return buffered;
    }

    //Resmi ölçeklendirir
    private static BufferedImage upscale(final Image image) {
        return toBufferedImage(image.getScaledInstance(image.getWidth(null) * 2,
                image.getHeight(null) * 2, Image.SCALE_FAST));
    }

    private int speed;          
    private int ticks;           
    private int ticksTubes;     

    private final List<Bird> birds = new ArrayList<Bird>(); 
    private final List<Tube> tubes = new ArrayList<Tube>(); 

    private Bird best;      //Her popülasyonun en iyi kuşu
    private int  score;     //Kuş kaç boruyu geçti

    //Boru konumuna göre kuş pozisyonunda gevşeterek giriş nöronları için girişleri hazırlar
    public void eval() {
        
        //Gelecek olan boru 
        Tube nextTube = null;
       
        /*
        Her tüpün en sağ pozisyonuna bakar ve ondan daha büyük olup olmadığını belirler: 
        1/3 Ekran genişliği + kuşun ortası. Temelde, kuşun henüz geçmediği en yakın 
        tüpü bulur ve bunu 'nextTube' olarak ayarlar.
        */
        for (final Tube tube : tubes)
            if (tube.position + TUBE_WIDTH > WIDTH / 3 - BIRD_WIDTH / 2
                    && (nextTube == null || tube.position < nextTube.position))
                nextTube = tube;
        
        //Her kuşa bakar. Ölü değilse, 4 giriş nöronunun her biri için uygun girdileri verecektir.
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;

            //Giriş nöronları için kullanılacak 4 double dizisi
            final double[] input = new double[4];
            //İlk girdi, kuşun geçerli yüksekliğine göre belirlendi
            input[0] = bird.height / HEIGHT;
            
            //Görünürde boru yoksa, giriş değerlerini varsayılan değerlere ayarla
            if (nextTube == null) {
                input[1] = 0.5;
                input[2] = 1.0;
            } 
            // Aksi takdirde, giriş değerlerini sonraki borunun koordinatlarına ayarlayın
            else {
                input[1] = nextTube.height / HEIGHT;
                input[2] = nextTube.position / WIDTH;
            }
            //Dördüncü girdi, boru boşluğunu ifade eden 1.0'a ayarlanır.
            input[3] = 1.0;

            //Eğer çıktı, 0.5'ten büyükse kuş zıplar
            final double[] output = bird.genome.evaluateNetwork(input);
            if (output[0] > 0.5)
                bird.flap = true;
        }
    }

    //Oyunun başlangıç değerleri
    public void initializeGame() {
        speed = 75;
        ticks = 0;
        ticksTubes = 0;
        best = null;
        score = 0;

        //Türün genomunda belirlenen parametrelere dayanarak yeni bir kuş havuzu yapar
        birds.clear();
        for (final Species species : Pool.species)
            for (final Genome genome : species.genomes) {
                genome.generateNetwork();
                birds.add(new Bird(species, genome));
            }
        tubes.clear();
    }

    //Uygunluğu mevcut 'maxFitness'tan büyükse, gruptaki en iyi kuşu bulur
    public void learn() {
        best = birds.get(0);
        boolean allDead = true;
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;
            allDead = false;

            double fitness = ticks - bird.flaps * 1.5;
            fitness = fitness == 0.0 ? -1.0 : fitness;

            //Kuşlar hayattayken uygunluklarını güncelle
            bird.genome.fitness = fitness;
            if (fitness > Pool.maxFitness)
                Pool.maxFitness = fitness;

            //En iyi kuşun uyugunluğu oyun ilerledikçe güncellenir
            if (fitness > best.genome.fitness)
                best = bird;
        }

        //Bütün kuşlar öldüyse, yeni bir jenerasyon başlat ve seviyeyi yeniden başlat
        if (allDead) {
            Pool.newGeneration();
            initializeGame();
        }
    }

    //Çizim işlemleri
    @Override
    public void paint(final Graphics g_) {
        final Graphics2D g2d = (Graphics2D) g_;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(BACK_IMAGE, 0, 0, WIDTH, HEIGHT, null);

        
        
        try {
            for (final Tube tube : tubes) {
            g2d.drawImage(TUBE1_IMAGE, (int) tube.position,
                    HEIGHT - (int) tube.height - TUBE_APERTURE - TUBE_HEIGHT,
                    TUBE_WIDTH, TUBE_HEIGHT, null);
            g2d.drawImage(TUBE2_IMAGE, (int) tube.position,
                    HEIGHT - (int) tube.height, TUBE_WIDTH, TUBE_HEIGHT, null);
        }
        } catch (Exception e) {
            System.out.println("Boru çizimi sırasında bir hata alındı.");
        }
    

        g2d.drawImage(GROUND_IMAGE,
                -(FLOOR_SPEED * ticks % (WIDTH - FLOOR_WIDTH)),
                HEIGHT - FLOOR_OFFSET, FLOOR_WIDTH, FLOOR_HEIGHT, null);

        int alive = 0;
        final int anim = ticks / 3 % 3;
        
        //Her kuş için eğer yaşıyorsa yaşayan kuş sayısını arttır
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;
            ++alive;
            
            //Kuş hareket ettikçe dönmesini sağlar
            final AffineTransform at = new AffineTransform();
            at.translate(WIDTH / 3 - BIRD_HEIGHT / 3, HEIGHT - bird.height);
            at.rotate(-bird.angle / 180.0 * Math.PI, BIRD_WIDTH / 2,
                    BIRD_HEIGHT / 2);
            //Kuşları çizer
            g2d.drawImage(bird.images[anim], at, null);
        }
        
    
        //Geçiş hızı
        g2d.setColor(Color.BLACK);
        Font trb = new Font("TimesRoman", Font.BOLD, 18);
        g2d.setFont(trb);
        g2d.drawString("Hızı Ayarlamak İçin Tıklayın", 160, 700);
        
        //Yaşayan kuş sayısını, belirtilen koordinata çiz
        g2d.drawString("" + alive +"/"+POPULATION + " alive", 470, 50);
        
       
        try {
        //Uygunluğu çiz
        g2d.drawString("Fitness " + best.genome.fitness + "/" + Pool.maxFitness,
                10, 50);
        } catch (Exception e) {
            System.out.println("Fitness çizilirken hata alındı");
        }
        
        //Jenerasyonu çiz
        g2d.drawString("Generation " + Pool.generation, 10, 80);
        
        //Skoru çiz
        g2d.setColor(Color.WHITE);
        trb = new Font("TimesRoman", Font.BOLD, 28);
        g2d.setFont(trb);
        g2d.drawString("" + score, WIDTH/2, 100);
        
    }
    
    //Her kuşun zıplamasını ve konumunu günceller
    public void update() {

        ++ticks;
        ++ticksTubes;

        //TicksTubes hıza eşit olduğunda, rasgele yüksekliğe sahip yeni bir tüp ekleme zamanı gelmiştir. ticksTubes 0'a sıfırlandı
        if (ticksTubes == speed) {
            final int height = FLOOR_OFFSET + 100
                    + rnd.nextInt(HEIGHT - 200 - TUBE_APERTURE - FLOOR_OFFSET);
            tubes.add(new Tube(height));
            ticksTubes = 0;
        }

        //Her bir boru boyunca tekrar eder ve ekrandan çıkıp çıkmadığını ve çıkarılması gerekip gerekmediğini belirler.
        //Kuşun mevcut boruyu geçip geçmediğini belirler ve geçtiyse puanı artırır
        final Iterator<Tube> it = tubes.iterator();
        while (it.hasNext()) {
            final Tube tube = it.next();
            tube.position -= FLOOR_SPEED;
            if (tube.position + TUBE_WIDTH < 0.0)
                it.remove();
            if (!tube.passed && tube.position + TUBE_WIDTH < WIDTH / 3
                    - BIRD_WIDTH / 2) {
                ++score;
                if (score % 10 == 0) {
                    speed -= 5;
                    speed = Math.max(speed, 20);
                }
                tube.passed = true;
            }
        }

        for (final Bird bird : birds) {
            
            //Kuş yandıysa bir sonraki kuşa git
            if (bird.dead)
                continue;

            if (bird.flap) {
                bird.velocity = 10;
                bird.flap = false;
                ++bird.flaps;
            }

            //Geçerli hızı ekleyerek yüksekliği ayarla
            bird.height += bird.velocity;
            //Hızı azalt
            bird.velocity -= 0.98;            
            //Açıyı 90 derecelik bir limite ayarla
            bird.angle = 3.0 * bird.velocity;
            bird.angle = Math.max(-90.0, Math.min(90.0, bird.angle));
           
            //Kuşun, ekranın üst sınırını aşmaması için gerekli işlemler
            if (bird.height > HEIGHT) {
                bird.height = HEIGHT;
                bird.velocity = 0.0;
                bird.angle = -bird.angle;
            }

            //Kuş yere çakıılrsa yansın
            if (bird.height < FLOOR_OFFSET + BIRD_HEIGHT / 2)
                bird.dead = true;

            final AffineTransform at = new AffineTransform();
            at.translate(WIDTH / 3 - BIRD_HEIGHT / 2, HEIGHT - bird.height);
            at.rotate(-bird.angle / 180.0 * Math.PI, BIRD_WIDTH / 2,
                    BIRD_HEIGHT / 2);
            at.translate(0, 52);
            final Shape bounds = new GeneralPath(BOUNDS)
                    .createTransformedShape(at);
            
            //Üst ve alt tüpleri kontrol et, çarptıysa kuş yansın
            for (final Tube tube : tubes) {
                final Rectangle2D ceilTube = new Rectangle2D.Double(
                        tube.position,
                        HEIGHT - tube.height - TUBE_APERTURE - TUBE_HEIGHT,
                        TUBE_WIDTH, TUBE_HEIGHT);
                final Rectangle2D floorTube = new Rectangle2D.Double(
                        tube.position, HEIGHT - tube.height, TUBE_WIDTH,
                        TUBE_HEIGHT);
                if (bounds.intersects(ceilTube)
                        || bounds.intersects(floorTube)) {
                    bird.dead = true;
                    break;
                }
            }
        }
    }

    @Override
    //Oyunu çalıştır
    public void run() {
        //Kuş popülasyonunun başlangıcı
        Pool.initializePool();
        //Oyunun başlangıcı
        initializeGame();
        
        //Ana oyun döngüsü
        while (true) {

            eval();
            update();
            learn();

            repaint();

            //Mouse yardımıyla oyunun hızı ayarlanır
            try {
                if(!speedUp)
                    Thread.sleep(20L);
                else
                    Thread.sleep(2L);
            } catch (final InterruptedException e) {
            }
        }
    }

    
    
}
