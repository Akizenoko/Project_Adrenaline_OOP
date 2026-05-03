import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

// ----- INTERFACE: Weapon -----
interface Weapon {
    int getBaseDamage();
    double getCritChance();  
    int getRange();
    String getName();
}

// ----- Weapon implementations -----
class Fists implements Weapon {
    public int getBaseDamage() { return 8; }
    public double getCritChance() { return 0.15; }
    public int getRange() { return 30; }
    public String getName() { return "Fists"; }
}

class Sword implements Weapon {
    public int getBaseDamage() { return 15; }
    public double getCritChance() { return 0.25; }
    public int getRange() { return 55; }
    public String getName() { return "Sword"; }
}

// ----- ABSTRACT CLASS: Character -----
abstract class Character {
    double x, y;
    double velX, velY;
    int health, maxHealth;
    int width, height;
    int groundY;
    boolean facingRight;

    public Character(int x, int y, int maxHealth) {
        this.x = x;
        this.y = y;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.groundY = y;
        this.facingRight = true;
        this.width = 40;
        this.height = 100;
    }

    abstract void draw(Graphics2D g);
    abstract Rectangle getHitbox();
    abstract void update(List<Character> others);

    public void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
    }

    public boolean isAlive() { return health > 0; }
}

// ----- Player class with RED color scheme and fixed flipping -----
class Player extends Character {
    enum State { IDLE, WALKING, JUMPING, ATTACKING }
    State state = State.IDLE;
    State previousState;
    Weapon currentWeapon = new Fists();
    int attackFrame = 0;
    final int ATTACK_DURATION = 15;   
    final int ATTACK_COOLDOWN = 25;
    int attackCooldown = 0;
    int comboCount = 0;
    int comboTimer = 0;
    final int COMBO_TIMEOUT = 90;
    boolean wasHitThisSwing = false;
    String lastCriticalText = "";
    int criticalTextTimer = 0;
    
    // SPRITE ANIMATION FIELDS
    private Map<State, List<BufferedImage>> animations;
    private BufferedImage currentSprite;
    private int animFrameIndex = 0;
    private int animTimer = 0;
    private final int FRAME_DELAY = 5;
    
    // Reference to game panel for boundary checks
    private StickFightGame gamePanel;
    
    public Player(int x, int y, StickFightGame panel) {
        super(x, y, 100);
        width = 40;
        height = 100;
        previousState = state;
        this.gamePanel = panel;
        generateSprites();
        currentSprite = animations.get(State.IDLE).get(0);
    }
    
    // ========== SPRITE GENERATION (RED PLAYER) ==========
    private void generateSprites() {
        animations = new HashMap<>();
        
        List<BufferedImage> idleFrames = new ArrayList<>();
        idleFrames.add(createStickmanSprite(0, 0, "IDLE"));
        animations.put(State.IDLE, idleFrames);
        
        List<BufferedImage> walkFrames = new ArrayList<>();
        walkFrames.add(createStickmanSprite(-25, 25, "WALK"));
        walkFrames.add(createStickmanSprite(-15, 15, "WALK"));
        walkFrames.add(createStickmanSprite(25, -25, "WALK"));
        walkFrames.add(createStickmanSprite(15, -15, "WALK"));
        animations.put(State.WALKING, walkFrames);
        
        List<BufferedImage> jumpFrames = new ArrayList<>();
        jumpFrames.add(createStickmanSprite(-45, 45, "JUMP"));
        animations.put(State.JUMPING, jumpFrames);
        
        List<BufferedImage> attackFrames = new ArrayList<>();
        attackFrames.add(createStickmanSprite(60, 10, "ATTACK"));
        attackFrames.add(createStickmanSprite(80, 20, "ATTACK"));
        attackFrames.add(createStickmanSprite(40, 10, "ATTACK"));
        animations.put(State.ATTACKING, attackFrames);
    }
    
    private BufferedImage createStickmanSprite(int armAngleOffset, int legAngleOffset, String actionText) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Clear background
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, width, height);
        g.setComposite(AlphaComposite.SrcOver);
        
        int centerX = width / 2;
        int headY = 10;
        int bodyTop = headY + 15;
        int bodyBottom = height - 20;
        
        // RED PLAYER: Head & body are red
        g.setColor(new Color(200, 50, 50));
        g.fillOval(centerX - 12, headY, 24, 24);
        // Black eyes
        g.setColor(Color.BLACK);
        g.fillOval(centerX - 6, headY + 6, 4, 4);
        g.fillOval(centerX + 2, headY + 6, 4, 4);
        // White pupils (optional)
        g.setColor(Color.WHITE);
        g.fillOval(centerX - 5, headY + 7, 2, 2);
        g.fillOval(centerX + 3, headY + 7, 2, 2);
        
        // Red body
        g.setColor(new Color(200, 50, 50));
        g.setStroke(new BasicStroke(3));
        g.drawLine(centerX, bodyTop, centerX, bodyBottom);
        
        // Arms and legs (dark red for contrast)
        g.setColor(new Color(150, 30, 30));
        int armLen = 30;
        double rad = Math.toRadians(armAngleOffset);
        int armX = centerX + (int)(armLen * Math.sin(rad));
        int armY = bodyTop + 10 + (int)(armLen * Math.cos(rad));
        g.drawLine(centerX, bodyTop + 10, armX, armY);
        int otherArmX = centerX - (int)(armLen * Math.sin(rad));
        g.drawLine(centerX, bodyTop + 10, otherArmX, armY);
        
        int legLen = 30;
        double legRad = Math.toRadians(legAngleOffset);
        int legX = centerX + (int)(legLen * Math.sin(legRad));
        int legY = bodyBottom + (int)(legLen * Math.cos(legRad));
        g.drawLine(centerX, bodyBottom, legX, legY);
        int legX2 = centerX - (int)(legLen * Math.sin(legRad));
        g.drawLine(centerX, bodyBottom, legX2, legY);
        
        // Weapon indicator
        if (currentWeapon instanceof Sword) {
            g.setColor(Color.LIGHT_GRAY);
            g.setStroke(new BasicStroke(4));
            g.drawLine(armX, armY, armX + 20, armY - 10);
            g.fillOval(armX + 18, armY - 12, 6, 6);
        } else {
            g.setColor(new Color(220, 180, 120));
            g.fillOval(armX - 3, armY - 3, 8, 8);
        }
        
        // Optional action text
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(Color.BLACK);
        g.drawString(actionText, centerX - 15, headY - 2);
        
        g.dispose();
        return img;
    }
    
    private void updateSpriteAnimation() {
        List<BufferedImage> currentAnim = animations.get(state);
        if (currentAnim == null || currentAnim.isEmpty()) return;
        
        if (previousState != state) {
            animFrameIndex = 0;
            animTimer = 0;
            previousState = state;
        }
        
        if (state == State.ATTACKING) {
            int attackSpriteIndex = 0;
            if (attackFrame >= 10) attackSpriteIndex = 2;
            else if (attackFrame >= 5) attackSpriteIndex = 1;
            else attackSpriteIndex = 0;
            if (attackSpriteIndex < currentAnim.size()) {
                currentSprite = currentAnim.get(attackSpriteIndex);
            } else {
                currentSprite = currentAnim.get(0);
            }
            return;
        }
        
        animTimer++;
        if (animTimer >= FRAME_DELAY) {
            animTimer = 0;
            animFrameIndex = (animFrameIndex + 1) % currentAnim.size();
            currentSprite = currentAnim.get(animFrameIndex);
        } else if (currentSprite == null) {
            currentSprite = currentAnim.get(0);
        }
    }
    
    public void setWeapon(Weapon w) { 
        currentWeapon = w;
        generateSprites(); // Refresh sprites to show new weapon
    }
    
    public Rectangle getHitbox() {
        return new Rectangle((int)x - width/2, (int)y - height, width, height);
    }
    
    public Rectangle getAttackHitbox() {
        int range = currentWeapon.getRange();
        int ah = 30;
        int ay = (int)y - height + 20;
        if (facingRight) {
            return new Rectangle((int)x + 15, ay, range, ah);
        } else {
            return new Rectangle((int)x - 15 - range, ay, range, ah);
        }
    }
    
    void update(List<Character> others) {
        if (state != State.ATTACKING) {
            velX *= 0.8;
        } else {
            velX *= 0.95;
        }
        x += velX;
        y += velY;
        if (y >= groundY) {
            y = groundY;
            velY = 0;
            if (state == State.JUMPING) state = State.IDLE;
        } else {
            velY += 0.6;
            if (velY > 0 && state != State.JUMPING) state = State.JUMPING;
        }
        
        // Screen boundaries (avoid going off edges)
        if (gamePanel != null) {
            int leftBound = 30;
            int rightBound = gamePanel.getWidth() - 30;
            x = Math.min(Math.max(x, leftBound), rightBound);
        }
        
        if (attackCooldown > 0) attackCooldown--;
        if (state == State.ATTACKING) {
            attackFrame++;
            if (attackFrame >= ATTACK_DURATION) {
                state = State.IDLE;
                attackFrame = 0;
                wasHitThisSwing = false;
            }
        }
        
        if (comboTimer > 0) comboTimer--;
        else comboCount = 0;
        if (criticalTextTimer > 0) criticalTextTimer--;
        
        updateSpriteAnimation();
    }
    
    void attack() {
        if (attackCooldown > 0 || state == State.ATTACKING) return;
        state = State.ATTACKING;
        attackFrame = 0;
        attackCooldown = ATTACK_COOLDOWN;
        wasHitThisSwing = false;
        animFrameIndex = 0;
        animTimer = 0;
    }
    
    void checkAttackHit(Character target) {
        if (!wasHitThisSwing && state == State.ATTACKING && attackFrame >= 5 && attackFrame <= 10) {
            Rectangle ab = getAttackHitbox();
            if (ab.intersects(target.getHitbox())) {
                wasHitThisSwing = true;
                int base = currentWeapon.getBaseDamage();
                boolean crit = Math.random() < currentWeapon.getCritChance();
                int dmg = crit ? base * 2 : base;
                target.takeDamage(dmg);
                comboCount++;
                comboTimer = COMBO_TIMEOUT;
                if (crit) {
                    lastCriticalText = "CRITICAL!";
                    criticalTextTimer = 30;
                }
            }
        }
    }
    
    @Override
    void draw(Graphics2D g) {
        // Fix flipping: draw sprite centered at x, correctly flipped
        int spriteX = (int)x - width/2;
        int spriteY = (int)y - height;
        
        if (facingRight) {
            g.drawImage(currentSprite, spriteX, spriteY, width, height, null);
        } else {
            // Flip around the center
            int drawX = spriteX + width;
            g.drawImage(currentSprite, drawX, spriteY, -width, height, null);
        }
        
        // Health bar
        int headY = (int)y - height + 10;
        int midX = (int)x;
        g.setColor(Color.RED);
        g.fillRect(midX - 20, headY - 15, 40, 5);
        g.setColor(Color.GREEN);
        int fillW = (int)(40 * (health / (double)maxHealth));
        g.fillRect(midX - 20, headY - 15, fillW, 5);
        
        // Combo & critical text
        g.setFont(new Font("Arial", Font.BOLD, 16));
        if (comboCount > 1 && comboTimer > 0) {
            g.setColor(Color.BLUE);
            g.drawString("COMBO x" + comboCount, midX - 30, headY - 25);
        }
        if (criticalTextTimer > 0) {
            g.setColor(Color.ORANGE);
            g.drawString(lastCriticalText, midX - 30, headY - 45);
        }
        
        // Attack hitbox debug
        if (state == State.ATTACKING) {
            g.setColor(new Color(255, 0, 0, 60));
            Rectangle ab = getAttackHitbox();
            g.fill(ab);
        }
    }
}

// ----- Enemy class with dark gray color -----
class Enemy extends Character {
    private BufferedImage enemySprite;
    
    public Enemy(int x, int y) {
        super(x, y, 80);
        generateEnemySprite();
    }
    
    private void generateEnemySprite() {
        enemySprite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) enemySprite.getGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, width, height);
        g.setComposite(AlphaComposite.SrcOver);
        
        int centerX = width / 2;
        int headY = 10;
        int bodyTop = headY + 15;
        int bodyBottom = height - 20;
        
        // Dark gray enemy
        g.setColor(new Color(80, 80, 100));
        g.fillOval(centerX - 12, headY, 24, 24);
        g.setColor(Color.WHITE);
        g.fillOval(centerX - 6, headY + 6, 4, 4);
        g.fillOval(centerX + 2, headY + 6, 4, 4);
        g.setColor(Color.BLACK);
        g.fillOval(centerX - 5, headY + 11, 2, 2);
        g.fillOval(centerX + 3, headY + 11, 2, 2);
        g.drawLine(centerX - 8, headY + 5, centerX - 3, headY + 7);
        g.drawLine(centerX + 8, headY + 5, centerX + 3, headY + 7);
        
        g.setColor(new Color(60, 60, 80));
        g.setStroke(new BasicStroke(3));
        g.drawLine(centerX, bodyTop, centerX, bodyBottom);
        g.drawLine(centerX, bodyTop + 10, centerX + 20, bodyTop + 30);
        g.drawLine(centerX, bodyTop + 10, centerX - 20, bodyTop + 30);
        g.drawLine(centerX, bodyBottom, centerX + 15, bodyBottom + 25);
        g.drawLine(centerX, bodyBottom, centerX - 15, bodyBottom + 25);
        
        g.dispose();
    }
    
    @Override
    public Rectangle getHitbox() {
        return new Rectangle((int)x - width/2, (int)y - height, width, height);
    }
    
    @Override
    void update(List<Character> others) { }
    
    @Override
    void draw(Graphics2D g) {
        g.drawImage(enemySprite, (int)x - width/2, (int)y - height, width, height, null);
        int headY = (int)y - height + 10;
        int midX = (int)x;
        g.setColor(Color.RED);
        g.fillRect(midX - 20, headY - 15, 40, 5);
        g.setColor(Color.GREEN);
        int fillW = (int)(40 * (health / (double)maxHealth));
        g.fillRect(midX - 20, headY - 15, fillW, 5);
    }
}

// ----- MAIN GAME PANEL -----
public class StickFightGame extends JPanel implements ActionListener, KeyListener {
    private Player player;
    private Enemy enemy;
    private List<Character> characters = new ArrayList<>();
    private Timer timer;
    private boolean leftPressed, rightPressed, upPressed;
    private String message = "";
    
    public StickFightGame() {
        setPreferredSize(new Dimension(800, 500));
        setBackground(new Color(30, 30, 45));
        setFocusable(true);
        addKeyListener(this);
        
        player = new Player(150, 400, this);
        enemy = new Enemy(550, 400);
        characters.add(player);
        characters.add(enemy);
        
        timer = new Timer(16, this);
        timer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        drawLabBackground(g2);
        
        for (Character c : characters) {
            c.draw(g2);
        }
        
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.drawString("Weapon: " + player.currentWeapon.getName() + " (press K to switch)", 20, 30);
        g2.drawString("Move: A / D   Jump: Space   Attack: J", 20, 50);
        g2.drawString("★ RED WARRIOR with FIXED FLIPPING & BOUNDARIES ★", 20, 70);
        
        if (!message.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 32));
            g2.setColor(new Color(100, 255, 100));
            g2.drawString(message, 260, 200);
        }
    }
    
    private void drawLabBackground(Graphics2D g) {
        g.setColor(new Color(60, 60, 80));
        g.fillRect(0, 400, getWidth(), 100);
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < 800; i += 50) {
            g.drawLine(i, 400, i, 500);
        }
        g.drawLine(0, 450, 800, 450);
        g.setColor(new Color(100, 100, 130));
        g.fillRect(0, 0, 20, 400);
        g.fillRect(780, 0, 20, 400);
        g.setColor(Color.GRAY);
        g.fillRect(300, 350, 60, 50);
        g.fillRect(500, 370, 80, 30);
        g.setColor(new Color(80, 200, 80));
        g.fillRect(310, 330, 40, 20);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if (leftPressed && player.state != Player.State.ATTACKING) {
            player.velX = -7;
            player.facingRight = false;
            if (player.state == Player.State.IDLE) player.state = Player.State.WALKING;
        }
        if (rightPressed && player.state != Player.State.ATTACKING) {
            player.velX = 7;
            player.facingRight = true;
            if (player.state == Player.State.IDLE) player.state = Player.State.WALKING;
        }
        if (upPressed && player.y >= player.groundY && player.state != Player.State.ATTACKING) {
            player.velY = -10;
            player.state = Player.State.JUMPING;
        }
        if (!leftPressed && !rightPressed && player.state == Player.State.WALKING) {
            player.state = Player.State.IDLE;
            player.velX = 0;
        }
        
        player.checkAttackHit(enemy);
        
        for (Character c : characters) {
            c.update(characters);
        }
        
        if (!enemy.isAlive() && message.isEmpty()) {
            message = "VICTORY! + COMBO MASTER";
        }
        
        repaint();
    }
    
    @Override public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A) leftPressed = true;
        if (code == KeyEvent.VK_D) rightPressed = true;
        if (code == KeyEvent.VK_SPACE) upPressed = true;
        if (code == KeyEvent.VK_J) player.attack();
        if (code == KeyEvent.VK_K) {
            if (player.currentWeapon instanceof Fists) player.setWeapon(new Sword());
            else player.setWeapon(new Fists());
        }
    }
    
    @Override public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A) leftPressed = false;
        if (code == KeyEvent.VK_D) rightPressed = false;
        if (code == KeyEvent.VK_SPACE) upPressed = false;
    }
    
    @Override public void keyTyped(KeyEvent e) {}
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("Stickman Arena - Red Warrior");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        StickFightGame game = new StickFightGame();
        frame.add(game);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}