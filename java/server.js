// server.js
// Import các module cần thiết
const express = require('express');
const mysql = require('mysql2/promise'); // Sử dụng mysql2/promise để hỗ trợ async/await
const bodyParser = require('body-parser');
const cors = require('cors');
const bcrypt = require('bcryptjs'); // Thêm thư viện để hash và so sánh password

// Cấu hình ứng dụng Express
const app = express();
const port = process.env.PORT || 3000; // Cổng server sẽ chạy

// Middleware
app.use(cors()); // Cho phép Cross-Origin Resource Sharing
app.use(bodyParser.json()); // Parse JSON request bodies
app.use(bodyParser.urlencoded({ extended: true })); // Parse URL-encoded request bodies

// Cấu hình kết nối MySQL
// QUAN TRỌNG: Thay thế bằng thông tin kết nối MySQL của bạn
const dbConfig = {
    host: 'localhost',        // Ví dụ: 'localhost' hoặc IP của server MySQL
    user: 'root',             // Thay bằng username MySQL của bạn
    password: 'Tien0610',       // Thay bằng password MySQL của bạn
    database: 'da_nang_gyms_db', // Thay bằng tên database của bạn
    connectionLimit: 10,
    multipleStatements: true
};

// Tạo một pool kết nối để quản lý các kết nối đến DB hiệu quả hơn
let pool;
try {
    pool = mysql.createPool(dbConfig);
    console.log("Đã kết nối thành công tới MySQL Pool!");
} catch (error) {
    console.error("Không thể kết nối tới MySQL Pool:", error);
    process.exit(1); // Thoát ứng dụng nếu không kết nối được DB
}


// --- ĐỊNH NGHĨA CÁC API ENDPOINTS ---

// Endpoint: Đăng ký người dùng
app.post('/api/auth/register', async (req, res) => {
    const { username, password, email, full_name } = req.body;

    if (!username || !password || !email) {
        return res.status(400).json({ message: 'Vui lòng cung cấp username, password và email.' });
    }

    try {
        // Hash mật khẩu trước khi lưu
        const salt = await bcrypt.genSalt(10);
        const hashedPassword = await bcrypt.hash(password, salt);
        console.log(`[Register API] Registering user: ${username}, Hashed Password: ${hashedPassword}`);


        const connection = await pool.getConnection();
        const [result] = await connection.query(
            'INSERT INTO users (username, password_hash, email, full_name) VALUES (?, ?, ?, ?)',
            [username, hashedPassword, email, full_name]
        );

        // Lấy thông tin user vừa tạo để trả về (không bao gồm password_hash)
        const [newUserRows] = await connection.query('SELECT id, username, email, full_name FROM users WHERE id = ?', [result.insertId]);
        connection.release();

        if (newUserRows.length > 0) {
            res.status(201).json({
                message: 'Đăng ký thành công!',
                user: newUserRows[0] // Trả về đối tượng user
            });
        } else {
            // Trường hợp hiếm gặp: không tìm thấy user vừa insert
            res.status(500).json({ message: 'Lỗi khi tạo người dùng mới.' });
        }

    } catch (error) {
        console.error('Lỗi khi đăng ký người dùng:', error);
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'Username hoặc email đã tồn tại.' });
        }
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

// Endpoint: Đăng nhập người dùng
app.post('/api/auth/login', async (req, res) => {
    const { username, password } = req.body;
    console.log(`[Login API] Attempting login for user: ${username}`);

    if (!username || !password) {
        return res.status(400).json({ message: 'Vui lòng cung cấp username và password.' });
    }

    try {
        const connection = await pool.getConnection();
        // Lấy đủ thông tin user cần thiết, bao gồm cả email và full_name
        const [rows] = await connection.query(
            'SELECT id, username, password_hash, email, full_name FROM users WHERE username = ?',
            [username]
        );
        connection.release();

        if (rows.length === 0) {
            console.log(`[Login API] User not found: ${username}`);
            return res.status(401).json({ message: 'Username không tồn tại.' });
        }

        const userFromDb = rows[0];
        console.log(`[Login API] User found in DB: ${userFromDb.username}, Hashed PW from DB: ${userFromDb.password_hash}`);
        console.log(`[Login API] Password from request: ${password}`);

        // QUAN TRỌNG: userFromDb.password_hash PHẢI là một chuỗi hash do bcrypt tạo ra.
        // Nếu nó là mật khẩu dạng văn bản thuần túy, bcrypt.compare sẽ luôn trả về false.
        // Đảm bảo rằng khi đăng ký, mật khẩu được hash bằng bcrypt.hash() và lưu vào DB.
        const passwordMatch = await bcrypt.compare(password, userFromDb.password_hash);
        console.log(`[Login API] Password match result for ${username}: ${passwordMatch}`);

        if (passwordMatch) {
            // Tạo đối tượng user để trả về, khớp với data class User của Android
            const userToReturn = {
                id: userFromDb.id,
                username: userFromDb.username,
                email: userFromDb.email,
                full_name: userFromDb.full_name // Đảm bảo tên trường khớp với DB
            };
            console.log(`[Login API] Login successful for ${username}. Returning user:`, userToReturn);
            res.json({
                message: 'Đăng nhập thành công!',
                user: userToReturn // Trả về đối tượng user đầy đủ
            });
        } else {
            console.log(`[Login API] Password mismatch for user: ${username}`);
            res.status(401).json({ message: 'Password không chính xác.' });
        }
    } catch (error) {
        console.error(`[Login API] Error during login for ${username}:`, error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});


// Endpoint: Lấy danh sách tất cả phòng gym
app.get('/api/gyms', async (req, res) => {
    try {
        const connection = await pool.getConnection();
        // QUAN TRỌNG: Đảm bảo bảng 'gyms' trong MySQL có các cột 'rating' và 'total_reviews'.
        // Xem lại file schema SQL nếu gặp lỗi "Unknown column".
        const [rows] = await connection.query('SELECT id, name, address, image_url, rating, total_reviews, latitude, longitude FROM gyms');
        connection.release();
        res.json(rows);
    } catch (error) {
        console.error('Lỗi khi lấy danh sách phòng gym:', error); // Đây là nơi log lỗi bạn thấy
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

// Endpoint: Lấy chi tiết một phòng gym theo ID
app.get('/api/gyms/:id', async (req, res) => {
    const gymId = req.params.id;
    try {
        const connection = await pool.getConnection();
        // QUAN TRỌNG: Đảm bảo bảng 'gyms' trong MySQL có các cột được liệt kê, bao gồm 'total_reviews'.
        const [rows] = await connection.query('SELECT * FROM gyms WHERE id = ?', [gymId]);
        connection.release();
        if (rows.length > 0) {
            const gym = rows[0];
            // Parse facilities nếu nó là JSON string
            if (gym.facilities && typeof gym.facilities === 'string') {
                try {
                    gym.facilities = JSON.parse(gym.facilities);
                } catch (parseError) {
                    console.error("Lỗi parse JSON facilities:", parseError);
                    gym.facilities = [];
                }
            } else if (!gym.facilities) {
                 gym.facilities = [];
            }
            res.json(gym);
        } else {
            res.status(404).json({ message: 'Không tìm thấy phòng gym' });
        }
    } catch (error) {
        console.error(`Lỗi khi lấy chi tiết phòng gym ID ${gymId}:`, error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

// Endpoint: Thêm phòng gym mới (ví dụ cho admin)
app.post('/api/gyms', async (req, res) => {
    // Trong ứng dụng thực tế, bạn cần xác thực đây là admin
    const { name, address, description, phone_number, opening_hours, image_url, latitude, longitude, facilities } = req.body;

    if (!name || !address) {
        return res.status(400).json({ message: 'Tên và địa chỉ phòng gym là bắt buộc.' });
    }

    try {
        const connection = await pool.getConnection();
        const [result] = await connection.query(
            'INSERT INTO gyms (name, address, description, phone_number, opening_hours, image_url, latitude, longitude, facilities) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
            [name, address, description, phone_number, opening_hours, image_url, latitude, longitude, facilities ? JSON.stringify(facilities) : null]
        );
        connection.release();
        res.status(201).json({ message: 'Thêm phòng gym thành công!', gymId: result.insertId });
    } catch (error) {
        console.error('Lỗi khi thêm phòng gym:', error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});


// Endpoint: Lấy đánh giá của một phòng gym
app.get('/api/gyms/:gymId/reviews', async (req, res) => {
    const { gymId } = req.params;
    try {
        const connection = await pool.getConnection();
        const [reviews] = await connection.query(
            `SELECT r.id, r.gym_id, r.user_id, u.username AS user_username, r.rating, r.comment, r.created_at
             FROM reviews r
             JOIN users u ON r.user_id = u.id
             WHERE r.gym_id = ? ORDER BY r.created_at DESC`,
            [gymId]
        );
        connection.release();
        res.json(reviews);
    } catch (error) {
        console.error(`Lỗi khi lấy đánh giá cho gym ID ${gymId}:`, error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

// Endpoint: Thêm đánh giá mới cho phòng gym
app.post('/api/gyms/:gymId/reviews', async (req, res) => {
    const { gymId } = req.params;
    const { userId, rating, comment } = req.body;

    if (userId == null || rating == null ) {
        return res.status(400).json({ message: 'Cần có User ID và Rating.' });
    }
    if (rating < 0 || rating > 5) {
        return res.status(400).json({ message: 'Rating phải từ 0 đến 5.' });
    }

    try {
        const connection = await pool.getConnection();
        const [result] = await connection.query(
            'INSERT INTO reviews (gym_id, user_id, rating, comment) VALUES (?, ?, ?, ?)',
            [gymId, userId, rating, comment]
        );
        const [newReview] = await connection.query(
             `SELECT r.id, r.gym_id, r.user_id, u.username AS user_username, r.rating, r.comment, r.created_at
             FROM reviews r
             JOIN users u ON r.user_id = u.id
             WHERE r.id = ?`,
            [result.insertId]
        );
        connection.release();
        res.status(201).json(newReview[0]);
    } catch (error) {
        console.error(`Lỗi khi thêm đánh giá cho gym ID ${gymId}:`, error);
        if (error.code === 'ER_NO_REFERENCED_ROW_2') {
             return res.status(404).json({ message: 'User ID hoặc Gym ID không hợp lệ.' });
        }
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

// --- FAVORITES API --- (Giữ nguyên các API favorites đã có)
app.post('/api/users/:userId/favorites', async (req, res) => {
    const { userId } = req.params;
    const { gymId } = req.body;
    if (!gymId) return res.status(400).json({ message: "Cần có Gym ID." });
    try {
        const connection = await pool.getConnection();
        await connection.query('INSERT INTO favorites (user_id, gym_id) VALUES (?, ?)', [userId, gymId]);
        connection.release();
        res.status(201).json({ message: "Đã thêm vào yêu thích." });
    } catch (error) {
        console.error('Lỗi khi thêm yêu thích:', error);
        if (error.code === 'ER_DUP_ENTRY') return res.status(409).json({ message: "Phòng gym này đã có trong danh sách yêu thích." });
        if (error.code === 'ER_NO_REFERENCED_ROW_2') return res.status(404).json({ message: 'User ID hoặc Gym ID không hợp lệ.' });
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

app.delete('/api/users/:userId/favorites/:gymId', async (req, res) => {
    const { userId, gymId } = req.params;
    try {
        const connection = await pool.getConnection();
        const [result] = await connection.query('DELETE FROM favorites WHERE user_id = ? AND gym_id = ?', [userId, gymId]);
        connection.release();
        if (result.affectedRows > 0) res.json({ message: "Đã xóa khỏi yêu thích." });
        else res.status(404).json({ message: "Không tìm thấy mục yêu thích này." });
    } catch (error) {
        console.error('Lỗi khi xóa yêu thích:', error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});

app.get('/api/users/:userId/favorites', async (req, res) => {
    const { userId } = req.params;
    try {
        const connection = await pool.getConnection();
        const [favoriteGyms] = await connection.query(
            `SELECT g.id, g.name, g.address, g.image_url, g.rating, g.total_reviews
             FROM gyms g
             JOIN favorites f ON g.id = f.gym_id
             WHERE f.user_id = ?`,
            [userId]
        );
        connection.release();
        res.json(favoriteGyms);
    } catch (error) {
        console.error('Lỗi khi lấy danh sách yêu thích:', error);
        res.status(500).json({ message: 'Lỗi máy chủ nội bộ', error: error.message });
    }
});


// Khởi động server
app.listen(port, () => {
    console.log(`Server API đang chạy tại http://localhost:${port}`);
});

// Xử lý khi đóng ứng dụng (Ctrl+C)
process.on('SIGINT', async () => {
    console.log("Đang đóng pool kết nối MySQL...");
    if (pool) {
        await pool.end();
        console.log("Pool kết nối MySQL đã đóng.");
    }
    process.exit(0);
});
