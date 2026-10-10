package de.tobias30.claims.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

/** Client-rendered Snake game, gated by a server-issued session token. */
final class SnakeScreen extends Screen {
    private static final int W=20,H=12,CELL=18;
    private final long token;
    private final ArrayDeque<Integer> body = new ArrayDeque<>();
    private int dx=1,dy=0,queuedX=1,queuedY=0,food,score,delay;
    private boolean over,sent;
    SnakeScreen(long token) { super(Component.literal("Snake")); this.token=token; reset(); }
    long session() { return token; }
    private void reset() { body.clear(); body.add(4*W+6); body.add(4*W+5); body.add(4*W+4); dx=1;dy=0;queuedX=1;queuedY=0;score=0;over=false;food=newFood(); }
    private int newFood() { int p; do { p=ThreadLocalRandom.current().nextInt(W*H); } while(body.contains(p)); return p; }
    @Override protected void init() {
        int top=(height-H*CELL)/2;
        addRenderableWidget(Button.builder(Component.literal("↑"), b->turn(0,-1)).bounds(width/2-22,top+H*CELL+8,44,20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), b->turn(-1,0)).bounds(width/2-70,top+H*CELL+8,44,20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b->turn(0,1)).bounds(width/2-22,top+H*CELL+30,44,20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), b->turn(1,0)).bounds(width/2+26,top+H*CELL+8,44,20).build());
        addRenderableWidget(Button.builder(Component.literal("Again / Close"), b->{ if(over) reset(); else onClose(); }).bounds(width/2-55,top+H*CELL+54,110,20).build());
    }
    private void turn(int x,int y) { if ((x != -dx || y != -dy) && (x != dx || y != dy)) { queuedX=x;queuedY=y; } }
    @Override public void tick() { if(over || ++delay<5)return; delay=0;dx=queuedX;dy=queuedY;int head=body.peekFirst(),x=head%W+dx,y=head/W+dy;if(x<0||x>=W||y<0||y>=H){over=true;return;}int next=y*W+x;boolean eat=next==food;if(body.contains(next)&&(eat||body.peekLast()!=next)){over=true;return;}body.addFirst(next);if(eat){score++;if(body.size()==W*H)over=true;else food=newFood();}else body.removeLast(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) { super.extractRenderState(g,mouseX,mouseY,delta);int left=(width-W*CELL)/2,top=(height-H*CELL)/2;g.centeredText(font,Component.literal("Snake  •  Score: "+score),width/2,top-24,0xFFFFFFFF);for(int i=0;i<W*H;i++){int x=left+(i%W)*CELL,y=top+(i/W)*CELL;g.fill(x,y,x+CELL-1,y+CELL-1,body.contains(i)?0xFF55CC66:i==food?0xFFE34F4F:0xFF242B35);}if(over)g.centeredText(font,Component.literal("Game over"),width/2,top+H*CELL/2,0xFFFFFFFF); }
    @Override public void onClose() { ClaimsClient.currentSnakeSession=Long.MIN_VALUE; ClaimsClient.activeSnake=null; if(!sent&&ClientPlayNetworking.canSend(Payloads.SnakeAction.TYPE)){ByteBuffer b=ByteBuffer.allocate(13).putInt(0x534E414B).put((byte)2).putLong(token);ClientPlayNetworking.send(new Payloads.SnakeAction(b.array()));sent=true;}super.onClose(); }
}
