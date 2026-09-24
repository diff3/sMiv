package nu.entropy.smiv;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Shows {@code sMiv NAV} / {@code sMiv INSERT} plus the command being typed or the last message.
 * Highlighted in INSERT, selection mode and while a command is typed; a click opens the sMiv menu.
 *
 * <p>Written in Java on purpose: a Kotlin class implementing StatusBarWidget gets a generated
 * override of the deprecated {@code getPresentation(PlatformType)}, which the Plugin Verifier reports.
 */
public final class SmivStatusBarWidget implements CustomStatusBarWidget {
    public static final String ID = "nu.entropy.smiv.status";
    private static final JBColor HIGHLIGHT = JBColor.namedColor("Banner.warningBackground", new JBColor(0xFFF4DB, 0x3D3223));

    private final JBLabel label = new JBLabel();

    public SmivStatusBarWidget(@NotNull Project project) {
        label.setBorder(JBUI.Borders.empty(0, 6));
        label.setToolTipText("sMiv mode and command line. Click for the sMiv menu.");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                SmivPopups.INSTANCE.showMenu(project, label);
            }
        });
        SmivService.Companion.get().addWidget(this);
        update();
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public JComponent getComponent() {
        return label;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    public void update() {
        SmivService service = SmivService.Companion.get();
        label.setText(service.statusText());
        label.setOpaque(service.getStatusHighlighted());
        label.setBackground(HIGHLIGHT);
        label.repaint();
    }

    @Override
    public void dispose() {
        SmivService.Companion.get().removeWidget(this);
    }
}
