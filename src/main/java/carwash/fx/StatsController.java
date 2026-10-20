package carwash.fx;

import carwash.service.Statistics;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

// контроллер окна статистики: просто показывает рассчитанные показатели.
public class StatsController {

    @FXML private Label clientsLabel;
    @FXML private Label totalLabel;
    @FXML private Label activeLabel;
    @FXML private Label completedLabel;
    @FXML private Label cancelledLabel;
    @FXML private Label revenueLabel;

    private Stage stage;

    public void init(Stage stage, Statistics stats) {
        this.stage = stage;
        clientsLabel.setText(String.valueOf(stats.getTotalClients()));
        totalLabel.setText(String.valueOf(stats.getTotalBookings()));
        activeLabel.setText(String.valueOf(stats.getActiveBookings()));
        completedLabel.setText(String.valueOf(stats.getCompletedBookings()));
        cancelledLabel.setText(String.valueOf(stats.getCancelledBookings()));
        revenueLabel.setText(String.format("%.2f", stats.getTotalRevenue()));
    }

    @FXML
    private void onClose() {
        stage.close();
    }
}
