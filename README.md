# Bài 30: Thiết Kế Tính Năng "Thêm Sản Phẩm Vào Giỏ Hàng"

Dự án này chứa tài liệu thiết kế hệ thống chi tiết cho tính năng **Thêm sản phẩm vào giỏ hàng** (Add to Cart) theo mô hình Layered Architecture / MVC (Spring Boot / Java style), bao gồm:
1. **Sơ đồ Lớp (Class Diagram)** bằng Mermaid.
2. **Sơ đồ Tuần tự (Sequence Diagram)** bằng Mermaid.
3. Kịch bản chi tiết bao gồm xử lý logic khi sản phẩm tồn tại/không tồn tại, giỏ hàng đã có mặt hàng hay tạo mới.

---

## 1. Sơ đồ Lớp (Class Diagram)

```mermaid
classDiagram
    class User {
        -Long id
        -String username
        -String email
        +getId() Long
        +getUsername() String
    }

    class Product {
        -Long id
        -String name
        -BigDecimal price
        -Integer stockQuantity
        +getId() Long
        +getPrice() BigDecimal
        +getStockQuantity() Integer
        +isAvailable(Integer quantity) Boolean
    }

    class CartItem {
        -Long id
        -Product product
        -Integer quantity
        -BigDecimal unitPrice
        +getQuantity() Integer
        +setQuantity(Integer quantity) void
        +increaseQuantity(Integer quantity) void
        +getSubtotal() BigDecimal
    }

    class Cart {
        -Long id
        -User user
        -List~CartItem~ items
        -LocalDateTime updatedAt
        +addItem(Product product, Integer quantity) void
        +findItemByProduct(Long productId) Optional~CartItem~
        +getTotalAmount() BigDecimal
    }

    class AddToCartRequest {
        -Long productId
        -Integer quantity
        +getProductId() Long
        +getQuantity() Integer
    }

    class CartResponse {
        -Long cartId
        -List~CartItemDto~ items
        -BigDecimal totalAmount
        -String message
    }

    class CartController {
        -CartService cartService
        +addToCart(Long userId, AddToCartRequest request) ResponseEntity~CartResponse~
    }

    class CartService {
        -CartRepository cartRepository
        -ProductRepository productRepository
        +addToCart(Long userId, Long productId, Integer quantity) CartResponse
    }

    class ProductRepository {
        <<interface>>
        +findById(Long id) Optional~Product~
    }

    class CartRepository {
        <<interface>>
        +findByUserId(Long userId) Optional~Cart~
        +save(Cart cart) Cart
    }

    User "1" -- "1" Cart : owns
    Cart "1" *-- "0..*" CartItem : contains
    CartItem "1" --> "1" Product : references
    CartController --> CartService : uses
    CartService --> ProductRepository : uses
    CartService --> CartRepository : uses
    CartController ..> AddToCartRequest : accepts
    CartController ..> CartResponse : returns
```

---

## 2. Sơ đồ Tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng (Client)
    participant Controller as CartController
    participant Service as CartService
    participant ProductRepo as ProductRepository
    participant CartRepo as CartRepository
    participant CartEntity as Cart
    participant ItemEntity as CartItem

    User->>Controller: POST /api/cart/items (userId, productId, quantity)
    activate Controller
    Controller->>Service: addToCart(userId, productId, quantity)
    activate Service

    %% Bước 1: Kiểm tra sản phẩm
    Service->>ProductRepo: findById(productId)
    activate ProductRepo
    ProductRepo-->>Service: Optional<Product>
    deactivate ProductRepo

    alt Sản phẩm không tồn tại
        Service-->>Controller: throw ProductNotFoundException("Sản phẩm không tồn tại")
        Controller-->>User: 404 Not Found (Error response)
    else Sản phẩm tồn tại
        %% Bước 2: Lấy hoặc tạo mới Giỏ hàng
        Service->>CartRepo: findByUserId(userId)
        activate CartRepo
        CartRepo-->>Service: Optional<Cart>
        deactivate CartRepo

        alt Giỏ hàng chưa tồn tại
            Service->>CartEntity: new Cart(user)
            activate CartEntity
            CartEntity-->>Service: cartInstance
            deactivate CartEntity
        end

        %% Bước 3: Thêm sản phẩm vào giỏ
        Service->>CartEntity: addItem(product, quantity)
        activate CartEntity
        CartEntity->>CartEntity: findItemByProduct(productId)
        
        alt Sản phẩm đã có trong giỏ
            CartEntity->>ItemEntity: increaseQuantity(quantity)
            activate ItemEntity
            ItemEntity-->>CartEntity: updated
            deactivate ItemEntity
        else Sản phẩm chưa có trong giỏ
            CartEntity->>ItemEntity: new CartItem(product, quantity)
            activate ItemEntity
            ItemEntity-->>CartEntity: newItem
            deactivate ItemEntity
            CartEntity->>CartEntity: items.add(newItem)
        end
        CartEntity-->>Service: updated Cart
        deactivate CartEntity

        %% Bước 4: Lưu thông tin giỏ hàng
        Service->>CartRepo: save(cart)
        activate CartRepo
        CartRepo-->>Service: savedCart
        deactivate CartRepo

        Service-->>Controller: CartResponse(savedCart)
        deactivate Service
        Controller-->>User: 200 OK (CartResponse)
        deactivate Controller
    end
```

---

## 3. Mô Tả Luồng Xử Lý (Use Case Detailed Logic)

1. **Tiếp nhận Yêu cầu**:
   - Controller tiếp nhận request `POST /api/cart/items` từ Người dùng (đã xác thực JWT/Session -> có `userId`).
2. **Kiểm tra Sản phẩm**:
   - Service gọi `ProductRepository.findById(productId)` để tìm thông tin sản phẩm X.
   - Nếu không tìm thấy, hệ thống dừng xử lý và trả về lỗi `404 Not Found`.
3. **Quản lý Giỏ hàng**:
   - Service kiểm tra người dùng đã có giỏ hàng trong CSDL chưa bằng `CartRepository.findByUserId(userId)`.
   - **Trường hợp A**: Chưa có giỏ hàng -> Tạo mới đối tượng `Cart` gán cho `userId`.
   - **Trường hợp B**: Đã có giỏ hàng -> Lấy đối tượng `Cart` hiện tại ra xử lý.
4. **Cập nhật Mặt hàng (CartItem)**:
   - Gọi phương thức `cart.addItem(product, quantity)`:
     - Kiểm tra sản phẩm X đã xuất hiện trong `cart.items` hay chưa.
     - Nếu **ĐÃ CÓ**: Tìm `CartItem` tương ứng và gọi `increaseQuantity(quantity)`.
     - Nếu **CHƯA CÓ**: Tạo một đối tượng `CartItem` mới với `product` và `quantity` rồi thêm vào danh sách `items`.
5. **Lưu dữ liệu & Trả về**:
   - Gọi `CartRepository.save(cart)` để lưu thông tin cập nhật vào CSDL.
   - Trả về DTO `CartResponse` thông báo thành công cho client kèm chi tiết giỏ hàng và tổng tiền.
