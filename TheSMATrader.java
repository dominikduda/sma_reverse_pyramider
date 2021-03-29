package jforex;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.feed.IFeedDescriptor;
import java.math.*;
import java.text.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

public class TheSMATrader implements IStrategy {
  private IEngine engine;
  private IConsole console;
  private IContext context;
  private IHistory history;
  private IIndicators indicators;
  private IUserInterface userInterface;

  private int ordersCounter;
  private int entriesClosedByRiskManagement = 0;
  private double prevBandCached = -1;
  private boolean volatileBarDetected = false;

  private double closeLimitLevelForStopLoss = -1;
  private double closeLimitLevel = 3;
  private boolean firstOrderTriggered = false;

  @Configurable("Instrument")
  public Instrument instrument = Instrument.EURUSD;

  public double prevBottomBand = -1;
  public double prevTopBand = -1;

  private IOrder order1 = null;
  private IOrder order2 = null;
  private IOrder order3 = null;
  private IOrder order4 = null;
  private IOrder order5 = null;
  private IOrder order6 = null;
  private IOrder order7 = null;
  private IOrder order8 = null;
  private IOrder order9 = null;
  private IOrder order10 = null;

  private double breakEvenLimit = -1;
  private double secondLimit = -1;
  private double thirdLimit = -1;
  private double fourthLimit = -1;
  private boolean skipNextExtremeSell = false;
  private double referenceATR = 0;
  private boolean topBandStretched = false;
  private boolean bottomBandStretched = false;
  private boolean breakoutDone = false;
  private boolean longCrossInThePast = false;
  private boolean shortCrossInThePast = false;
  private double prevSMAFlash = 0;
  private double prevSMASecond = 0;
  private int entriesLeftForCross = 2;
  private boolean entryLong = false;
  private boolean entryShort = false;
  private double stopLossPrice = -1;

  private int crossCandlesToInvalidate = 15;
  private int candlesInTrade = 0;



  private boolean highRocInThePast = false;
  private boolean lowRocInThePast = false;
  private double highRocCandlesToInvalidate = 0;
  private double lowRocCandlesToInvalidate = 0;

  private IFeedDescriptor feedDescriptor15;
  private IFeedDescriptor feedDescriptor5;

  public void onStart(IContext context) throws JFException {
    this.engine = context.getEngine();
    this.console = context.getConsole();
    this.history = context.getHistory();
    this.context = context;
    this.indicators = context.getIndicators();
    this.userInterface = context.getUserInterface();
    this.ordersCounter = 0;
    // Do subscribe selected instrument
    Set subscribedInstruments = new HashSet();
    subscribedInstruments.add(this.instrument);
    context.setSubscribedInstruments(subscribedInstruments);
    int ordersTotal = engine.getOrders(this.instrument).size();

    // comment_for_debug
    // if (ordersTotal < 1) {
    //   throw new JFException("1 or more orders needed!");
    // }
    // IOrder firstOrder = engine.getOrders(this.instrument).get(0);
    this.breakEvenLimit = 0;
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
        case ORDER_CLOSE_OK:

        case ORDERS_MERGE_OK:
          // console.getInfo().println(orderLabel +" "+ messageType);
          break;
        case NOTIFICATION:
          // console.getNotif().println(orderLabel +" "+ message.getContent().replaceAll(".*-Order",
          // "Order"));
          break;
        case ORDER_CHANGED_REJECTED:
        case ORDER_CLOSE_REJECTED:
        case ORDER_FILL_REJECTED:
        case ORDER_SUBMIT_REJECTED:
        case ORDERS_MERGE_REJECTED:
          // console.getWarn().println(orderLabel +" "+ messageType);
          break;
        default:
          // console.getErr().println(orderLabel +" *"+ messageType +"* "+ message.getContent());
          break;
      }
    }
  }

  public void onStop() throws JFException {}

  public void onTick(Instrument instrument, ITick tick) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    int ordersTotal = 0;
    ordersTotal = engine.getOrders(this.instrument).size();
    List<IOrder> orders = engine.getOrders(this.instrument);

    if (this.order1 == null && ordersTotal >= 1) {
      this.order1 = orders.get(0);
    }
    if (this.order2 == null && ordersTotal >= 2) {
      this.order2 = orders.get(1);
    }
    if (this.order3 == null && ordersTotal >= 3) {
      this.order3 = orders.get(2);
    }
    if (this.order4 == null && ordersTotal >= 4) {
      this.order4 = orders.get(3);
    }
    if (this.order5 == null && ordersTotal >= 5) {
      this.order5 = orders.get(4);
    }
    if (this.order6 == null && ordersTotal >= 6) {
      this.order6 = orders.get(5);
    }
    if (this.order7 == null && ordersTotal >= 7) {
      this.order7 = orders.get(6);
    }
    if (this.order8 == null && ordersTotal >= 8) {
      this.order8 = orders.get(7);
    }
    if (this.order9 == null && ordersTotal >= 9) {
      this.order9 = orders.get(8);
    }
    if (this.order10 == null && ordersTotal >= 10) {
      this.order10 = orders.get(9);
    }

    // breakeven limit is also used as a switch
    if (ordersTotal > 0 && this.breakEvenLimit == -1) {
      double lastATR =
          indicators.atr(this.instrument, Period.FIFTEEN_MINS, stopLossOfferSide(), 8, 1);
      this.referenceATR = lastATR;
      if (isLong()) {
        this.breakEvenLimit = this.order1.getOpenPrice() + 1.8 * lastATR;
        this.secondLimit = this.order1.getOpenPrice() + 2.4 * lastATR;
        this.thirdLimit = this.order1.getOpenPrice() + 3.2 * lastATR;
        this.fourthLimit = this.order1.getOpenPrice() + 4.5 * lastATR;
      }
      if (!isLong()) {
        this.breakEvenLimit = this.order1.getOpenPrice() - 1.8 * lastATR;
        this.secondLimit = this.order1.getOpenPrice() - 2.4 * lastATR;
        this.thirdLimit = this.order1.getOpenPrice() - 3.2 * lastATR;
        this.fourthLimit = this.order1.getOpenPrice() - 4.5 * lastATR;
      }
    }

    if (filledOrdersCount() == 0) {
      // comment for debug
      // context.stop();
    }
  }

  public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar)
      throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    Period timeFrame = null;
    int ordersTotal = engine.getOrders(this.instrument).size();

    if (ordersTotal > 0 && this.breakEvenLimit != -1 && period == Period.ONE_MIN) {
      // this.breakEvenLimit = this.order1.getOpenPrice() - 0.8 * lastATR;
      // this.secondLimit = this.order1.getOpenPrice() - 1.6 * lastATR;
      // this.thirdLimit = this.order1.getOpenPrice() - 3.2 * lastATR;
      // this.fourthLimit = this.order1.getOpenPrice() - 4.5 * lastATR;
      if (isLong()) {
        if (bidBar.getClose() >= this.breakEvenLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice());
          this.breakoutDone = true;
        }
        if (bidBar.getClose() >= this.secondLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() + 1.8 * this.referenceATR);
        }
        if (bidBar.getClose() >= this.thirdLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() + 2.4 * this.referenceATR);
        }
        if (bidBar.getClose() >= this.fourthLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() + 3.2 * this.referenceATR);
        }
      }
      if (!isLong()) {
        if (askBar.getClose() <= this.breakEvenLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice());
          this.breakoutDone = true;
        }
        if (askBar.getClose() <= this.secondLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() - 1.8 * this.referenceATR);
        }
        if (askBar.getClose() <= this.thirdLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() + 2.4 * this.referenceATR);
        }
        if (askBar.getClose() <= this.fourthLimit) {
          setAllStopLossesTo(this.order1.getOpenPrice() + -3.2 * this.referenceATR);
        }
      }
    }

    if (period != Period.FIFTEEN_MINS) {
      return;
    }
    double lastATR = indicators.atr(this.instrument, Period.FIFTEEN_MINS, OfferSide.ASK, 8, 1);

    double roc =
        indicators.roc(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 16, 0);
    if (roc > 0.5) {
      this.highRocInThePast = true;
      this.highRocCandlesToInvalidate = 15;
      console.getOut().println("Przesadny ruch w górę wykryty. Szukanie shorta.");
    }
    if (roc < -0.5) {
      this.lowRocInThePast = true;
      this.lowRocCandlesToInvalidate = 15;
      console.getOut().println("Przesadny ruch w dół wykryty. Szukanie longa.");
    }
    if (this.lowRocCandlesToInvalidate != 0) {
      this.lowRocCandlesToInvalidate--;
    }
    if (this.highRocCandlesToInvalidate != 0) {
      this.highRocCandlesToInvalidate--;
    }
    if (this.lowRocCandlesToInvalidate == 0) {
      this.lowRocInThePast = false;
    }
    if (this.highRocCandlesToInvalidate == 0) {
      this.highRocInThePast = false;
    }


    if (candlesInTrade > 3 && !this.breakoutDone) {
      closeAllOrders();
    }

    // top bonds ribon calculation
    // List<IBar> askBarsTemp = history.getBars(instrument, Period.FIFTEEN_MINS, OfferSide.ASK,
    // Filter.WEEKENDS, 14, askBar.getTime(), 0);
    // double localMax = Double.NEGATIVE_INFINITY;
    // double localMin = Double.POSITIVE_INFINITY;
    // for (IBar bar : askBarsTemp) {
    //   if (bar.getHigh() > localMax) {
    //     localMax = bar.getHigh();
    //   }
    //   if (bar.getLow() < localMin) {
    //     localMin = bar.getLow();
    //   }
    // }

    // if (ordersTotal == 0 && Math.abs(localMax - localMin) < 2.5 * lastATR) {
    //   console.getOut().println("Za mały range, oczekiwanie na odpowiedni.");
    //   return;
    // }

    this.crossCandlesToInvalidate--;
    if (ordersTotal > 0) {
      this.candlesInTrade++;
      IOrder firstOrder = engine.getOrders(this.instrument).get(0);
      if (candlesInTrade > 3 && !this.breakoutDone) {
        closeAllOrders();
      }
    }

    double SMAflash =
        indicators.sma(
            instrument, Period.FIFTEEN_MINS, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 6, 0);
    double SMAsecond =
        indicators.sma(
            instrument, Period.FIFTEEN_MINS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);

    if (ordersTotal == 0) {
      if (SMAflash > SMAsecond && prevSMAFlash < prevSMASecond) {
        this.longCrossInThePast = true;
        this.crossCandlesToInvalidate = 6;
        console.getOut().println("Przecięcie średnich na longa wykryte");
      }
      if (SMAflash < SMAsecond && prevSMAFlash > prevSMASecond) {
        this.shortCrossInThePast = true;
        this.crossCandlesToInvalidate = 6;
        console.getOut().println("Przecięcie średnich na shorta wykryte");
      }
    }
    if (this.longCrossInThePast && bidBar.getLow() > SMAflash && this.lowRocInThePast) {
      console.getOut().println("Odlatująca świeczka na long wykryta");
      this.stopLossPrice = bidBar.getClose() - lastATR * 3;
      // this.stopLossPrice = bidBar.getLow();
      this.entryLong = true;
      this.entriesLeftForCross = 1;
    }
    if (this.shortCrossInThePast && askBar.getHigh() < SMAflash && this.highRocInThePast) {
      console.getOut().println("Odlatująca świeczka na short wykryta");
      this.stopLossPrice = askBar.getClose() + lastATR * 3;
      // this.stopLossPrice = bidBar.getHigh();
      this.entryShort = true;
      this.entriesLeftForCross = 1;
    }

    if (entriesLeftForCross == 0) {
      this.longCrossInThePast = false;
      this.shortCrossInThePast = false;
      this.entryLong = false;
      this.entryShort = false;
    }
    timeFrame = Period.FIFTEEN_MINS;

    cleanClosedOrders();
    if (instrument == this.instrument
        && (this.entryLong || this.entryShort)
        && this.entriesLeftForCross > 0
        && ordersTotal == 0) {
      this.candlesInTrade = 0;
      this.entriesLeftForCross--;
      IBar lastBar = getBar(timeFrame, OfferSide.ASK, 1);
      boolean justChanged = false;
      if (this.entryShort) {
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.SELL,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
      }
      if (this.entryLong) {
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
        engine
            .submitOrder(
                getLabel(instrument),
                instrument,
                OrderCommand.BUY,
                0.002,
                askBar.getClose(),
                3.0,
                getRoundedPrice(this.stopLossPrice),
                0)
            .waitForUpdate(100);
      }
      this.breakoutDone = false;
      this.order1 = null;
      this.skipNextExtremeSell = false;
      // this.order2 = null;
      // this.order3 = null;
      // this.order4 = null;
      // this.order5 = null;
      // this.order6 = null;
      // this.order7 = null;
      // this.order8 = null;
      // this.order9 = null;
      // this.order10 = null;
      this.breakEvenLimit = -1;
      this.secondLimit = -1;
      this.thirdLimit = -1;
      this.fourthLimit = -1;
      this.referenceATR = 0;
      return;
    }

    // TOP AND BOTTOM BAND generation
    long prevBarTime = history.getPreviousBarStart(Period.FIFTEEN_MINS, askBar.getTime());
    List<IBar> askBarsX =
        history.getBars(
            this.instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            14,
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
            14,
            bidBar.getTime(),
            0);
    double bottomBand = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBarsX) {
      if (bar.getLow() < bottomBand) {
        bottomBand = bar.getLow();
      }
    }
    if (ordersTotal > 0 && bottomBand < this.prevBottomBand && this.prevBottomBand != -1) {
      this.bottomBandStretched = true;
      console.getOut().println("Dolna banda przebita");
    }
    if (ordersTotal > 0 && topBand > this.prevTopBand && this.prevTopBand != -1) {
      this.topBandStretched = true;
      console.getOut().println("Górna banda przebita");
    }
    if (ordersTotal > 0
        && isLong()
        && this.topBandStretched
        && bidBar.getClose() < bidBar.getOpen()
        && bidBar.getClose() >= this.secondLimit) {
      if (this.skipNextExtremeSell) {
        this.skipNextExtremeSell = false;
        this.topBandStretched = false;
      } else {
        for (IOrder order : engine.getOrders(this.instrument)) {
          if (order.getProfitLossInPips() > 0
              && order.getId() != this.order3.getId()
              && order.getId() != this.order6.getId()) {
            if (ordersTotal <= 8) {
              this.skipNextExtremeSell = true;
            }
            order.close();
            order.waitForUpdate(2000);
            order.waitForUpdate(2000);
            this.topBandStretched = false;
            break;
          }
        }
      }
    }
    ;
    if (ordersTotal > 0
        && !isLong()
        && this.bottomBandStretched
        && askBar.getClose() > askBar.getOpen()
        && askBar.getClose() <= this.secondLimit) {
      if (this.skipNextExtremeSell) {
        this.bottomBandStretched = false;
        this.skipNextExtremeSell = false;
      } else {
        for (IOrder order : engine.getOrders(this.instrument)) {
          if (order.getProfitLossInPips() > 0
              && order.getId() != this.order3.getId()
              && order.getId() != this.order6.getId()) {
            if (ordersTotal <= 8) {
              this.skipNextExtremeSell = true;
            }
            order.close();
            order.waitForUpdate(2000);
            order.waitForUpdate(2000);
            this.bottomBandStretched = false;
            break;
          }
        }
      }
    }
    this.prevTopBand = topBand;
    this.prevBottomBand = bottomBand;

    // top bonds ribon calculation
    List<IBar> askBars2 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            44,
            askBar.getTime(),
            0);
    double topBaseBand = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars2) {
      if (bar.getHigh() > topBaseBand) {
        topBaseBand = bar.getHigh();
      }
    }
    List<IBar> askBars3 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            66,
            askBar.getTime(),
            0);
    double topFastBand = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars3) {
      if (bar.getHigh() > topFastBand) {
        topFastBand = bar.getHigh();
      }
    }
    List<IBar> askBars4 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            99,
            askBar.getTime(),
            0);
    double topFastBand2 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars4) {
      if (bar.getHigh() > topFastBand2) {
        topFastBand2 = bar.getHigh();
      }
    }
    List<IBar> askBars5 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            132,
            askBar.getTime(),
            0);
    double topFastBand3 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars5) {
      if (bar.getHigh() > topFastBand3) {
        topFastBand3 = bar.getHigh();
      }
    }
    List<IBar> askBars6 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            161,
            askBar.getTime(),
            0);
    double topFastBand4 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars6) {
      if (bar.getHigh() > topFastBand4) {
        topFastBand4 = bar.getHigh();
      }
    }
    List<IBar> askBars =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            200,
            askBar.getTime(),
            0);
    double topSlowBand = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars) {
      if (bar.getHigh() > topSlowBand) {
        topSlowBand = bar.getHigh();
      }
    }
    List<IBar> askBars7 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            14,
            askBar.getTime(),
            0);
    double topFastBand5 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars7) {
      if (bar.getHigh() > topFastBand5) {
        topFastBand5 = bar.getHigh();
      }
    }
    List<IBar> askBars8 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            3,
            askBar.getTime(),
            0);
    double topFastBand6 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars8) {
      if (bar.getHigh() > topFastBand6) {
        topFastBand6 = bar.getHigh();
      }
    }
    List<IBar> askBars9 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            33,
            askBar.getTime(),
            0);
    double topFastBand7 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars9) {
      if (bar.getHigh() > topFastBand7) {
        topFastBand7 = bar.getHigh();
      }
    }

    List<IBar> askBars10 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.ASK,
            Filter.WEEKENDS,
            21,
            askBar.getTime(),
            0);
    double topFastBand8 = Double.NEGATIVE_INFINITY;
    for (IBar bar : askBars10) {
      if (bar.getHigh() > topFastBand8) {
        topFastBand8 = bar.getHigh();
      }
    }

    // bottom bonds ribon calculation
    List<IBar> bidBars =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            200,
            bidBar.getTime(),
            0);
    double botSlowBand = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars) {
      if (bar.getLow() < botSlowBand) {
        botSlowBand = bar.getLow();
      }
    }
    List<IBar> bidBars2 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            44,
            bidBar.getTime(),
            0);
    double botBaseBand = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars2) {
      if (bar.getLow() < botBaseBand) {
        botBaseBand = bar.getLow();
      }
    }
    List<IBar> bidBars3 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            67,
            bidBar.getTime(),
            0);
    double botFastBand = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars3) {
      if (bar.getLow() < botFastBand) {
        botFastBand = bar.getLow();
      }
    }
    List<IBar> bidBars4 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            99,
            bidBar.getTime(),
            0);
    double botFastBand2 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars4) {
      if (bar.getLow() < botFastBand2) {
        botFastBand2 = bar.getLow();
      }
    }
    List<IBar> bidBars5 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            132,
            bidBar.getTime(),
            0);
    double botFastBand3 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars5) {
      if (bar.getLow() < botFastBand3) {
        botFastBand3 = bar.getLow();
      }
    }
    List<IBar> bidBars10 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            166,
            bidBar.getTime(),
            0);
    double botFastBand4 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars10) {
      if (bar.getLow() < botFastBand4) {
        botFastBand4 = bar.getLow();
      }
    }
    List<IBar> bidBars6 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            3,
            bidBar.getTime(),
            0);
    double botFastBand5 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars6) {
      if (bar.getLow() < botFastBand5) {
        botFastBand5 = bar.getLow();
      }
    }
    List<IBar> bidBars7 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            21,
            bidBar.getTime(),
            0);
    double botFastBand6 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars7) {
      if (bar.getLow() < botFastBand6) {
        botFastBand6 = bar.getLow();
      }
    }
    List<IBar> bidBars8 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            14,
            bidBar.getTime(),
            0);
    double botFastBand7 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars8) {
      if (bar.getLow() < botFastBand7) {
        botFastBand7 = bar.getLow();
      }
    }
    List<IBar> bidBars9 =
        history.getBars(
            instrument,
            Period.FIFTEEN_MINS,
            OfferSide.BID,
            Filter.WEEKENDS,
            33,
            bidBar.getTime(),
            0);
    double botFastBand8 = Double.POSITIVE_INFINITY;
    for (IBar bar : bidBars9) {
      if (bar.getLow() < botFastBand8) {
        botFastBand8 = bar.getLow();
      }
    }

    if (ordersTotal > 0 && isLong()) {
      if (this.order1 != null
          && this.order1.getState() == IOrder.State.FILLED
          && this.order1.getStopLossPrice() < botFastBand
          && this.breakoutDone) {
        this.order1.setStopLossPrice(botFastBand);
        this.order1.waitForUpdate(2000);
        this.order1.waitForUpdate(2000);
      }
      if (this.order2 != null
          && this.order2.getState() == IOrder.State.FILLED
          && this.order2.getStopLossPrice() < botBaseBand
          && this.breakoutDone) {
        this.order2.setStopLossPrice(botBaseBand);
        this.order2.waitForUpdate(2000);
        this.order2.waitForUpdate(2000);
      }
      if (this.order3 != null
          && this.order3.getState() == IOrder.State.FILLED
          && this.order3.getStopLossPrice() < botSlowBand
          && this.breakoutDone) {
        this.order3.setStopLossPrice(botSlowBand);
        this.order3.waitForUpdate(2000);
        this.order3.waitForUpdate(2000);
      }
      if (this.order4 != null
          && this.order4.getState() == IOrder.State.FILLED
          && this.order4.getStopLossPrice() < botFastBand2
          && this.breakoutDone) {
        this.order4.setStopLossPrice(botFastBand2);
        this.order4.waitForUpdate(2000);
        this.order4.waitForUpdate(2000);
      }
      if (this.order5 != null
          && this.order5.getState() == IOrder.State.FILLED
          && this.order5.getStopLossPrice() < botFastBand3
          && this.breakoutDone) {
        this.order5.setStopLossPrice(botFastBand3);
        this.order5.waitForUpdate(2000);
        this.order5.waitForUpdate(2000);
      }
      if (this.order6 != null
          && this.order6.getState() == IOrder.State.FILLED
          && this.order6.getStopLossPrice() < botFastBand4
          && this.breakoutDone) {
        this.order6.setStopLossPrice(botFastBand4);
        this.order6.waitForUpdate(2000);
        this.order6.waitForUpdate(2000);
      }
      if (this.order7 != null
          && this.order7.getState() == IOrder.State.FILLED
          && this.order7.getStopLossPrice() < botFastBand5
          && this.breakoutDone) {
        this.order7.setStopLossPrice(botFastBand5);
        this.order7.waitForUpdate(2000);
        this.order7.waitForUpdate(2000);
      }
      if (this.order8 != null
          && this.order8.getState() == IOrder.State.FILLED
          && this.order8.getStopLossPrice() < botFastBand6
          && this.breakoutDone) {
        this.order8.setStopLossPrice(botFastBand6);
        this.order8.waitForUpdate(2000);
        this.order8.waitForUpdate(2000);
      }
      if (this.order9 != null
          && this.order9.getState() == IOrder.State.FILLED
          && this.order9.getStopLossPrice() < botFastBand7
          && this.breakoutDone) {
        this.order9.setStopLossPrice(botFastBand7);
        this.order9.waitForUpdate(2000);
        this.order9.waitForUpdate(2000);
      }
      if (this.order10 != null
          && this.order10.getState() == IOrder.State.FILLED
          && this.order10.getStopLossPrice() < botFastBand8
          && this.breakoutDone) {
        this.order10.setStopLossPrice(botFastBand8);
        this.order10.waitForUpdate(2000);
        this.order10.waitForUpdate(2000);
      }
    }
    if (ordersTotal > 0 && !isLong()) {
      if (this.order1 != null
          && this.order1.getState() == IOrder.State.FILLED
          && this.order1.getStopLossPrice() > topFastBand
          && this.breakoutDone) {
        this.order1.setStopLossPrice(topFastBand);
        this.order1.waitForUpdate(2000);
        this.order1.waitForUpdate(2000);
      }
      if (this.order2 != null
          && this.order2.getState() == IOrder.State.FILLED
          && this.order2.getStopLossPrice() > topBaseBand
          && this.breakoutDone) {
        this.order2.setStopLossPrice(topBaseBand);
        this.order2.waitForUpdate(2000);
        this.order2.waitForUpdate(2000);
      }
      if (this.order3 != null
          && this.order3.getState() == IOrder.State.FILLED
          && this.order3.getStopLossPrice() > topSlowBand
          && this.breakoutDone) {
        this.order3.setStopLossPrice(topSlowBand);
        this.order3.waitForUpdate(2000);
        this.order3.waitForUpdate(2000);
      }
      if (this.order4 != null
          && this.order4.getState() == IOrder.State.FILLED
          && this.order4.getStopLossPrice() > topFastBand2
          && this.breakoutDone) {
        this.order4.setStopLossPrice(topFastBand2);
        this.order4.waitForUpdate(2000);
        this.order4.waitForUpdate(2000);
      }
      if (this.order5 != null
          && this.order5.getState() == IOrder.State.FILLED
          && this.order5.getStopLossPrice() > topFastBand3
          && this.breakoutDone) {
        this.order5.setStopLossPrice(topFastBand3);
        this.order5.waitForUpdate(2000);
        this.order5.waitForUpdate(2000);
      }
      if (this.order6 != null
          && this.order6.getState() == IOrder.State.FILLED
          && this.order6.getStopLossPrice() > topFastBand4
          && this.breakoutDone) {
        this.order6.setStopLossPrice(topFastBand4);
        this.order6.waitForUpdate(2000);
        this.order6.waitForUpdate(2000);
      }
      if (this.order7 != null
          && this.order7.getState() == IOrder.State.FILLED
          && this.order7.getStopLossPrice() > topFastBand5
          && this.breakoutDone) {
        this.order7.setStopLossPrice(topFastBand5);
        this.order7.waitForUpdate(2000);
        this.order7.waitForUpdate(2000);
      }
      if (this.order8 != null
          && this.order8.getState() == IOrder.State.FILLED
          && this.order8.getStopLossPrice() > topFastBand6
          && this.breakoutDone) {
        this.order8.setStopLossPrice(topFastBand6);
        this.order8.waitForUpdate(2000);
        this.order8.waitForUpdate(2000);
      }
      if (this.order9 != null
          && this.order9.getState() == IOrder.State.FILLED
          && this.order9.getStopLossPrice() > topFastBand7
          && this.breakoutDone) {
        this.order9.setStopLossPrice(topFastBand7);
        this.order9.waitForUpdate(2000);
        this.order9.waitForUpdate(2000);
      }
      if (this.order10 != null
          && this.order10.getState() == IOrder.State.FILLED
          && this.order10.getStopLossPrice() > topFastBand8
          && this.breakoutDone) {
        this.order10.setStopLossPrice(topFastBand8);
        this.order10.waitForUpdate(2000);
        this.order10.waitForUpdate(2000);
      }
    }

    this.prevSMAFlash = SMAflash;
    this.prevSMASecond = SMAsecond;
    this.entryLong = false;
    this.entryShort = false;
  }

  protected double getRoundedPrice(double price) {
    BigDecimal bd = new BigDecimal(price);
    bd = bd.setScale(this.instrument.getPipScale() + 1, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  protected void closeAllOrders() throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      order.close();
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

  protected String getLabel(Instrument instrument) {
    return instrument.name().substring(0, 2)
        + instrument.name().substring(3, 5)
        + "DDtrails"
        + this.context.getTime()
        + Integer.toString(++this.ordersCounter);
  }

  protected boolean canEnterLong() throws JFException {
    double SMAflashH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 0);
    double SMAsecondH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
    if (SMAflashH4 >= SMAsecondH4) {
      return true;
    } else {
      return false;
    }
  }

  void cleanClosedOrders() throws JFException {
    if (this.order1 != null && this.order1.getState() != IOrder.State.FILLED) {
      this.order1 = null;
    }
    if (this.order2 != null && this.order2.getState() != IOrder.State.FILLED) {
      this.order2 = null;
    }
    if (this.order3 != null && this.order3.getState() != IOrder.State.FILLED) {
      this.order3 = null;
    }
    if (this.order4 != null && this.order4.getState() != IOrder.State.FILLED) {
      this.order4 = null;
    }
    if (this.order5 != null && this.order5.getState() != IOrder.State.FILLED) {
      this.order5 = null;
    }
    if (this.order6 != null && this.order6.getState() != IOrder.State.FILLED) {
      this.order6 = null;
    }
    if (this.order7 != null && this.order7.getState() != IOrder.State.FILLED) {
      this.order7 = null;
    }
    if (this.order8 != null && this.order8.getState() != IOrder.State.FILLED) {
      this.order8 = null;
    }
    if (this.order9 != null && this.order9.getState() != IOrder.State.FILLED) {
      this.order9 = null;
    }
    if (this.order10 != null && this.order10.getState() != IOrder.State.FILLED) {
      this.order10 = null;
    }
  }
}
