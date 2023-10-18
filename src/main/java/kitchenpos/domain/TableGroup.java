package kitchenpos.domain;

import org.springframework.util.CollectionUtils;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class TableGroup {

    private static final int MIN_TABLE_SIZE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private LocalDateTime createdDate;
    @OneToMany(mappedBy = "tableGroup", cascade = CascadeType.PERSIST)
    private List<OrderTable> orderTables;

    public TableGroup() {
        this.createdDate = LocalDateTime.now();
    }

    public TableGroup(Long id, List<OrderTable> orderTables) {
        this.id = id;
        this.createdDate = LocalDateTime.now();
        this.orderTables = orderTables;
    }

    public TableGroup(List<OrderTable> orderTables) {
        this.id = null;
        this.createdDate = LocalDateTime.now();
        this.orderTables = orderTables;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public List<OrderTable> getOrderTables() {
        return orderTables;
    }

    public void setOrderTables(final List<OrderTable> orderTables) {
        validateOrderTableSize(orderTables);
        this.orderTables = orderTables;
        for (OrderTable orderTable : orderTables) {
            orderTable.setTableGroup(this);
        }
    }

    private void validateOrderTableSize(final List<OrderTable> orderTables) {
        if (CollectionUtils.isEmpty(orderTables) || orderTables.size() < MIN_TABLE_SIZE) {
            throw new IllegalArgumentException("단체 지정 테이블의 개수는 2개 이상이어야 합니다.");
        }
    }

    public void unGroup() {
        for (final OrderTable orderTable : orderTables) {
            orderTable.setTableGroup(null);
            orderTable.changeEmpty(true);
        }
        orderTables.clear();
    }
}
