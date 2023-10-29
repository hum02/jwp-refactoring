package kitchenpos;

import kitchenpos.menu.Menu;
import kitchenpos.menu.MenuGroup;
import kitchenpos.menu.MenuGroupRepository;
import kitchenpos.menu.MenuRepository;
import kitchenpos.order.Order;
import kitchenpos.order.OrderLineItem;
import kitchenpos.order.OrderLineItemRepository;
import kitchenpos.order.OrderRepository;
import kitchenpos.order.OrderStatus;
import kitchenpos.order.application.OrderService;
import kitchenpos.order.ui.OrderLineItemDto;
import kitchenpos.order.ui.OrderRequest;
import kitchenpos.order.ui.OrderResponse;
import kitchenpos.ordertable.OrderTable;
import kitchenpos.ordertable.OrderTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static kitchenpos.order.OrderStatus.COMPLETION;
import static kitchenpos.order.OrderStatus.MEAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.groups.Tuple.tuple;

@ServiceTest
class OrderServiceTest {

    @Autowired
    MenuGroupRepository menuGroupRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private OrderTableRepository orderTableRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderLineItemRepository orderLineItemRepository;

    private MenuGroup menuGroup;
    private Menu menu;
    private OrderTable orderTable;
    private OrderLineItem orderLineItem;
    private OrderLineItemDto orderLineItemDto;
    private Order order;

    @BeforeEach
    void setUp() {
        menuGroup = menuGroupRepository.save(new MenuGroup("메뉴 그룹"));
        menu = menuRepository.save(new Menu("메뉴", BigDecimal.valueOf(30000), menuGroup));
        orderTable = orderTableRepository.save(new OrderTable(null, 1, false));
        order = orderRepository.save(new Order(orderTable.getId(), MEAL.name(), LocalDateTime.now()));
        orderLineItem = orderLineItemRepository.save(new OrderLineItem(order, menu.getName(), menu.getPrice(), 1));
        orderLineItemDto = new OrderLineItemDto(order.getId(), menu.getId(), 2L);
    }

    @Nested
    class 주문등록 {

        @Test
        void 주문을_등록한다() {
            OrderRequest orderRequest = new OrderRequest(orderTable.getId(), MEAL.name(), LocalDateTime.now(), List.of(orderLineItemDto));

            OrderResponse response = orderService.create(orderRequest);

            assertSoftly(softly -> {
                softly.assertThat(response.getId()).isNotNull();

                softly.assertThat(response).extracting("orderTableId", "orderStatus")
                        .containsOnly(orderRequest.getOrderTableId(), orderRequest.getOrderStatus());

                List<String> orderNames = response.getOrderLineItems().stream()
                        .map(OrderLineItem::getName)
                        .collect(Collectors.toList());
                softly.assertThat(orderNames).containsOnly(orderLineItem.getName());
            });
        }

        @Test
        void 주문_항목이_존재하지_않으면_등록할_수_없다() {
            OrderRequest orderRequest = new OrderRequest(orderTable.getId(), MEAL.name(), LocalDateTime.now(), List.of());

            assertThatThrownBy(() -> orderService.create(orderRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("주문 항목이 존재하지 않습니다. 주문을 등록할 수 없습니다.");
        }

        @Test
        void 주문_항목에_존재하지_않는_메뉴가_있으면_등록할_수_없다() {
            Menu notExistMenu = new Menu(1000L, "name", BigDecimal.ONE, menuGroup);
            OrderLineItemDto wrongOrderLineItem = new OrderLineItemDto(order.getId(), notExistMenu.getId(), 2L);
            OrderRequest orderRequest = new OrderRequest(orderTable.getId(), MEAL.name(),
                    LocalDateTime.now(), List.of(orderLineItemDto, wrongOrderLineItem));

            assertThatThrownBy(() -> orderService.create(orderRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("주문항목에 존재하지 않는 메뉴가 있습니다. 주문을 등록할 수 없습니다.");
        }

        @Test
        void 해당하는_주문_테이블이_존재하지_않으면_등록할_수_없다() {
            long notExistId = Long.MIN_VALUE;
            OrderRequest orderRequest = new OrderRequest(notExistId, null, LocalDateTime.now(), List.of(orderLineItemDto));

            assertThatThrownBy(() -> orderService.create(orderRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("주문 테이블이 존재하지 않습니다. 주문을 등록할 수 없습니다.");
        }
    }

    @Nested
    class 주문상태_변경 {
        @Test
        void 주문_상태를_변경할_수_있다() {
            OrderResponse orderResponse = orderService.create(
                    new OrderRequest(orderTable.getId(), MEAL.name(), LocalDateTime.now(), List.of(orderLineItemDto))
            );

            OrderRequest orderRequest = new OrderRequest(orderResponse.getOrderTableId(), orderResponse.getOrderStatus(),
                    orderResponse.getOrderedTime(), List.of(orderLineItemDto));
            OrderResponse changed = orderService.changeOrderStatus(orderResponse.getId(), orderRequest);

            assertThat(changed.getOrderStatus()).isEqualTo(MEAL.name());
        }

        @Test
        void 주문이_존재하지_않으면_상태를_변경할_수_없다() {
            long notExistOrder = 100000L;
            assertThatThrownBy(() -> orderService.changeOrderStatus(notExistOrder, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("주문이 존재하지 않습니다. 주문상태를 변경할 수 없습니다.");
        }

        @Test
        void 주문이_이미_완료된_경우_상태를_변경할_수_없다() {
            OrderResponse orderResponse = orderService.create(
                    new OrderRequest(orderTable.getId(), COMPLETION.name(), LocalDateTime.now(), List.of(orderLineItemDto))
            );

            OrderRequest changeRequest = new OrderRequest(orderResponse.getOrderTableId(), OrderStatus.COOKING.name(),
                    orderResponse.getOrderedTime(), List.of(orderLineItemDto));

            assertThatThrownBy(() -> orderService.changeOrderStatus(orderResponse.getId(), changeRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("주문이 이미 완료되었습니다. 주문상태를 변경할 수 없습니다.");
        }
    }

    @Test
    void 주문목록을_조회한다() {
        OrderResponse order2 = orderService.create(
                new OrderRequest(orderTable.getId(), MEAL.name(), LocalDateTime.now(), List.of(orderLineItemDto))
        );
        OrderResponse order3 = orderService.create(
                new OrderRequest(orderTable.getId(), MEAL.name(), LocalDateTime.now(), List.of(orderLineItemDto))
        );

        List<OrderResponse> orderResponses = orderService.list();

        assertThat(orderResponses).hasSize(3)
                .extracting("id", "orderedTime")
                .containsExactly(tuple(order.getId(), order.getOrderedTime()),
                        tuple(order2.getId(), order2.getOrderedTime()),
                        tuple(order3.getId(), order3.getOrderedTime())
                );
    }
}
