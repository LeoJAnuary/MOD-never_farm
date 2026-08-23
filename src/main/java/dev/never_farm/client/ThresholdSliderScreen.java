package dev.never_farm.client;

import dev.never_farm.network.C2SSetThreshold;
import dev.never_farm.network.S2CBlockData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;



@OnlyIn(Dist.CLIENT)
public class ThresholdSliderScreen extends Screen {

	private static final long SEND_INTERVAL_MS = 100L;
	private static final int WINDOW_WIDTH = 220;
	private static final int WINDOW_HEIGHT = 82;
	private static final int[] LEVELS = {2, 4, 6};

	private final BlockPos pos;
	private int threshold;
	private int maxSlots;
	private String boundType;
	private int storedCount;
	private boolean typeLocked;
	private int feedCount;
	private long lastSend;
	private int ticksOpen;
	private int lastSent = -1;

	private int guiLeft;
	private int guiTop;

	public ThresholdSliderScreen(BlockPos pos) {
		super(Component.translatable("gui.never_farm.threshold"));
		this.pos = pos;
		this.threshold = 0;
		this.maxSlots = 32;
	}

	public BlockPos pos() {
		return pos;
	}

	public void updateData(S2CBlockData data) {
		this.maxSlots = data.maxSlots();
		int raw = data.threshold();
		this.threshold = raw <= 2 ? 2 : (raw >= 6 ? 6 : 4);
		this.boundType = data.boundType().orElse(null);
		this.storedCount = data.storedCount();
		this.typeLocked = data.typeLocked();
		this.feedCount = data.feedCount();
	}

	


	@Override
	protected void init() {
		guiLeft = (width - WINDOW_WIDTH) / 2;
		guiTop = (height - WINDOW_HEIGHT) / 2;
		super.init();
		

		setCursor(getCoordinateOfValue(threshold));
	}

	

	private void setCursor(Vec2 coordinate) {
		double guiScale = minecraft.getWindow().getGuiScale();
		GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
			coordinate.x * guiScale, coordinate.y * guiScale);
	}

	

	private Vec2 getCoordinateOfValue(int value) {
		int x0 = sliderLeft();
		int x1 = sliderRight();
		int idx = Math.max(0, Math.min(LEVELS.length - 1, (value - 2) / 2));
		float x = Mth.lerp(idx / (float) (LEVELS.length - 1), x0, x1);
		return new Vec2(x, sliderY());
	}

	

	private int getClosestValue(double mouseX, boolean milestonesOnly) {
		double t = Mth.clamp((mouseX - sliderLeft()) / (double) (sliderRight() - sliderLeft()), 0.0D, 1.0D);
		int idx = (int) Math.round(t * (LEVELS.length - 1));
		idx = Math.max(0, Math.min(LEVELS.length - 1, idx));
		return LEVELS[idx];
	}

	private int sliderLeft() {
		return guiLeft + 18;
	}

	private int sliderRight() {
		return guiLeft + WINDOW_WIDTH - 18;
	}

	private int sliderY() {
		return guiTop + 50;
	}

	


	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g, mouseX, mouseY, partialTick);
		int cx = guiLeft + WINDOW_WIDTH / 2;
		int y = guiTop;

		g.drawCenteredString(font, title, cx, y + 2, 0xFFFFFF);
		g.drawCenteredString(font, Component.translatable("gui.never_farm.threshold_value", threshold),
			cx, y + 14, 0xFFFF55);
		g.drawCenteredString(font, Component.translatable("gui.never_farm.type",
				boundType != null ? boundType : "?"),
			cx, y + 24, 0xCCCCCC);
		g.drawCenteredString(font, Component.translatable("gui.never_farm.stored", storedCount, maxSlots),
			cx, y + 33, 0xCCCCCC);

		

		int sy = sliderY();
		g.fill(sliderLeft() - 2, sy - 2, sliderRight() + 2, sy + 2, 0xFF442000);
		g.fill(sliderLeft(), sy - 1, sliderRight(), sy + 1, 0xFF8A5A2B);
		for (int level : LEVELS) {
			int tx = (int) getCoordinateOfValue(level).x;
			g.fill(tx, sy - 3, tx + 1, sy + 3, 0xFFFBDC7D);
		}
		

		int hx = (int) getCoordinateOfValue(threshold).x;
		g.fill(hx - 14, sy - 6, hx + 14, sy + 6, 0xFFFBDC7D);
		g.fill(hx - 13, sy - 5, hx + 13, sy + 5, 0xFF442000);
		g.drawCenteredString(font, Component.literal(String.valueOf(threshold)), hx, sy - 4, 0xFFFBDC7D);

		g.drawCenteredString(font, Component.translatable("gui.never_farm.drag_hint"),
			cx, sy + 18, 0x777777);
	}

	@Override
	public void tick() {
		ticksOpen++;
		super.tick();
	}

	


	

	@Override
	public void mouseMoved(double mx, double my) {
		if (ticksOpen >= 2) {
			threshold = getClosestValue(mx, hasShiftDown());
			sendThrottled();
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int idx = Math.max(0, Math.min(LEVELS.length - 1, (threshold - 2) / 2));
		idx = Mth.clamp(idx + (int) Math.signum(scrollY), 0, LEVELS.length - 1);
		threshold = LEVELS[idx];
		sendThrottled();
		setCursor(getCoordinateOfValue(threshold));
		return true;
	}

	

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
			saveAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (minecraft.options.keyUse.matchesMouse(button)) {
			saveAndClose();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public void onClose() {
		sendNow();
		super.onClose();
	}

	private void saveAndClose() {
		sendNow();
		onClose();
	}

	private void sendThrottled() {
		long now = System.currentTimeMillis();
		if (now - lastSend < SEND_INTERVAL_MS) {
			return;
		}
		lastSend = now;
		sendNow();
	}

	private void sendNow() {
		if (threshold == lastSent) {
			return;
		}
		lastSent = threshold;
		PacketDistributor.sendToServer(new C2SSetThreshold(pos, threshold));
	}
}
