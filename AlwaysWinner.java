package jforex;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import java.math.*;
import java.text.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

public class AlwaysWinner implements IStrategy {
  private IEngine engine;
  private IConsole console;
  private IContext context;
  private IHistory history;
  private IIndicators indicators;
  private IUserInterface userInterface;

  @Configurable("Instrument")
  public Instrument instrument = null;

  private boolean prevIsUptrend = false;

  @Configurable("Limit level after 7 bars")
  public double limitLevelFor7Bars = -1;

  @Configurable("Safety limit level for close")
  public double limitLevelForClose = -1;

  @Configurable("Minimal profit pips")
  public double minimalProfitPips = 2;

  @Configurable("Ignore initial candle for 'reverse color' close")
  public boolean ignoreInitialCandle = false;

  private int closedM15CandlesTillBotStart = 0;
  private boolean shouldCloseAllOrders = false;

  private int ordersCounter = 0;
  private boolean topBandStretched = false;
  private boolean bottomBandStretched = false;
  private boolean initialTradeLong = false;
  private boolean initialTradeShort = false;

  private double topFilterLimit = -1;
  private double botFilterLimit = -1;
  private double prevTopBand = -1;
  private double prevBottomBand = -1;
  private IOrder orderWaitingForFill = null;

  private double topPrice = -1;
  private double midTopPrice = -1;
  private double bottomPrice = -1;
  private double midBottomPrice = -1;
  private double prevNextAmount = -1;

  public void onStart(IContext context) throws JFException {
    this.console = context.getConsole();
    this.history = context.getHistory();
    this.engine = context.getEngine();
    this.context = context;
    this.indicators = context.getIndicators();
    this.userInterface = context.getUserInterface();
    // Do subscribe selected instrument
    Set subscribedInstruments = new HashSet();
    subscribedInstruments.add(this.instrument);
    context.setSubscribedInstruments(subscribedInstruments);
  }

  public void onAccount(IAccount account) throws JFException {}

  public void onMessage(IMessage message) throws JFException {
    // Print messages, but related to own orders
    if (message.getOrder() != null) {
      if (message.getOrder().getInstrument() != this.instrument) {
        return;
      }
      IMessage.Type messageType = message.getType();
      switch (messageType) {
        case ORDER_FILL_OK:
          break;
        case ORDER_CHANGED_OK:
          break;
        case ORDER_SUBMIT_OK:
          break;
        case ORDER_CLOSE_OK:
          if (filledOrdersCount() == 0) {
            closeAllOrders();
            this.initialTradeLong = false;
            this.initialTradeShort = false;
            this.orderWaitingForFill = null;
            this.topPrice = -1;
            this.midTopPrice = -1;
            this.midBottomPrice = -1;
            this.bottomPrice = -1;
            this.prevNextAmount = -1;
          }
          break;
        case ORDERS_MERGE_OK:
          break;
        case NOTIFICATION:
          break;
        case ORDER_CHANGED_REJECTED:
        case ORDER_CLOSE_REJECTED:
        case ORDER_FILL_REJECTED:
        case ORDER_SUBMIT_REJECTED:
        case ORDERS_MERGE_REJECTED:
          break;
        default:
          break;
      }
    }
  }

  public void onStop() throws JFException {}

  public void onTick(Instrument instrument, ITick tick) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    if (this.orderWaitingForFill != null
        && this.orderWaitingForFill.getState() == IOrder.State.FILLED) {

      int filledOrdersCount = 0;
      filledOrdersCount = filledOrdersCount();
      double nextAmount = -1;

      switch (filledOrdersCount) {
        case 2:
          nextAmount = 0.01;
          break;
        case 3:
          nextAmount = 0.014;
          break;
        case 4:
          nextAmount = 0.019;
          break;
        case 5:
          nextAmount = 0.025;
          break;
        case 6:
          nextAmount = 0.033;
          break;
        case 7:
          nextAmount = 0.044;
          break;
        default:
          nextAmount = this.prevNextAmount * 1.33;
          break;
      }
      this.prevNextAmount = nextAmount;

      if (this.orderWaitingForFill.getOrderCommand() == OrderCommand.SELL) {
        this.orderWaitingForFill =
            engine.submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUYSTOP,
                nextAmount,
                this.midTopPrice,
                3,
                this.bottomPrice,
                this.topPrice);
      }
      if (this.orderWaitingForFill.getOrderCommand() == OrderCommand.BUY) {
        this.orderWaitingForFill =
            engine.submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELLSTOP,
                nextAmount,
                this.midBottomPrice,
                3,
                this.topPrice,
                this.bottomPrice);
      }
    }
  }

  public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar)
      throws JFException {
    if (instrument != this.instrument || period != Period.FIFTEEN_MINS) {
      return;
    }

    int ordersTotal = 0;
    ordersTotal = engine.getOrders(this.instrument).size();

    // TOP AND BOTTOM BAND generation
    long prevBarTime = history.getPreviousBarStart(Period.FIFTEEN_MINS, askBar.getTime());
    List<IBar> askBarsX =
        history.getBars(
            this.instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            50,
            askBar.getTime(),
            0);
    double topBand = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBarsX) {
      if (bar.getHigh() > topBand) {
        topBand = bar.getHigh();
      }
    }
    List<IBar> bidBarsX =
        history.getBars(
            this.instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            50,
            bidBar.getTime(),
            0);
    double bottomBand = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBarsX) {
      if (bar.getLow() < bottomBand) {
        bottomBand = bar.getLow();
      }
    }
    if (bottomBand < this.prevBottomBand && this.prevBottomBand != -1) {
      this.bottomBandStretched = true;
      console.getOut().println("Dolna banda przebita");
    }
    if (topBand > this.prevTopBand && this.prevTopBand != -1) {
      this.topBandStretched = true;
      console.getOut().println("Górna banda przebita");
    }

    if (this.bottomBandStretched && ordersTotal == 0) {
      // this.topPrice = getRoundedPrice(askBar.getClose() + 60 * instrument.getPipValue());
      // this.midTopPrice = getRoundedPrice(askBar.getClose());
      // this.midBottomPrice = getRoundedPrice(askBar.getClose() - 20 * instrument.getPipValue());
      // this.bottomPrice = getRoundedPrice(askBar.getClose() - 80 * instrument.getPipValue());
      this.topPrice = getRoundedPrice(askBar.getClose() + 80 * instrument.getPipValue());
      this.midTopPrice = getRoundedPrice(askBar.getClose());
      this.midBottomPrice = getRoundedPrice(askBar.getClose() - 20 * instrument.getPipValue());
      this.bottomPrice = getRoundedPrice(askBar.getClose() - 100 * instrument.getPipValue());

      engine.submitOrder(
          getLabel(instrument),
          instrument,
          OrderCommand.BUY,
          0.01,
          this.midTopPrice,
          3,
          this.bottomPrice,
          this.topPrice);
      this.orderWaitingForFill =
          engine.submitOrder(
              getLabel(instrument),
              instrument,
              OrderCommand.SELLSTOP,
              0.014,
              this.midBottomPrice,
              3,
              this.topPrice,
              this.bottomPrice);
      this.initialTradeLong = true;
    }

    this.prevTopBand = topBand;
    this.prevBottomBand = bottomBand;
    this.topBandStretched = false;
    this.bottomBandStretched = false;
  }

  protected double getRoundedPrice(double price) {
    BigDecimal bd = new BigDecimal(price);
    bd = bd.setScale(this.instrument.getPipScale() + 1, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  protected void closeAllOrders() throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (order.getState() == IOrder.State.OPENED) {
        order.setRequestedAmount(0);
      }
      if (order.getState() == IOrder.State.FILLED) {
        order.close();
      }
    }
  }

  protected int filledOrdersCount() throws JFException {
    int filledOrdersTotal = 0;
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (order.getState() == IOrder.State.FILLED) {
        filledOrdersTotal += 1;
      }
    }
    return filledOrdersTotal;
  }

  protected int totalOrdersCount() throws JFException {
    int filledOrdersTotal = 0;
    for (IOrder order : engine.getOrders(this.instrument)) {
      filledOrdersTotal += 1;
    }
    return filledOrdersTotal;
  }

  void setAllStopLossesTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (isLong() && getRoundedPrice(price) > order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      if (!isLong() && getRoundedPrice(price) < order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
    }
  }

  void setAllTakeProfitsTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      order.setTakeProfitPrice(price);
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
    }
  }

  // void handleTooMuchRisk() throws JFException {
  //   int nonBreakEvenOrdersCount = 0;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (isLong()) {
  //         if (order.getOpenPrice() > order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       } else {
  //         if (order.getOpenPrice() < order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       }
  //     }
  //   }
  //   if (nonBreakEvenOrdersCount > 2 && entriesClosedByRiskManagement <
  // maxEntriesClosedByRiskManagement) {
  //     entriesClosedByRiskManagement++;
  //     orderWithBiggestProfit().close();
  //   }
  // }

  // IOrder orderWithBiggestProfit() throws JFException {
  //   IOrder result = null;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (result == null || result.getProfitLossInUSD() < order.getProfitLossInUSD()) {
  //         result = order;
  //       }
  //     }
  //   }
  //   return result;
  // }

  boolean isLong() throws JFException {
    List<IOrder> orders = engine.getOrders(this.instrument);
    return orders.get(0).isLong();
  }

  boolean barColorRightForFastTrails(IBar bar) throws JFException {
    boolean isRed = bar.getClose() < bar.getOpen();
    boolean result = false;
    if (isLong()) {
      if (!isRed) {
        result = true;
      }
    }
    if (!isLong()) {
      if (isRed) {
        result = true;
      }
    }
    return result;
  }

  protected IBar getBar(Period timeFrame, OfferSide marketSide, int index) throws JFException {
    IBar lastBar = history.getBar(this.instrument, timeFrame, marketSide, index);
    return (lastBar);
  }

  OfferSide stopLossOfferSide() throws JFException {
    if (isLong()) {
      return OfferSide.BID;
    } else {
      return OfferSide.ASK;
    }
  }

  protected boolean canEnterLong() throws JFException {
    double SMAflashH4 =
        indicators.sma(
            instrument, Period.FIFTEEN_MINS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 0);
    double SMAsecondH4 =
        indicators.sma(
            instrument, Period.FIFTEEN_MINS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
    if (SMAflashH4 >= SMAsecondH4) {
      return true;
    } else {
      return false;
    }
  }

  protected String getLabel(Instrument instrument) {
    return instrument.name().substring(0, 2)
        + instrument.name().substring(3, 5)
        + "DDtrails"
        + this.context.getTime()
        + Integer.toString(++this.ordersCounter);
  }
}
