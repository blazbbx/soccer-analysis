import numpy as np


# TODO Finish this class, now is mock (kind of)
class CornerPredictor:
    def predict_corners(self, image: np.ndarray) -> list[tuple[int, int]]:
        """Returns 4 corners of the field given in pixels."""
        height, width = image.shape[:2]
        edge_size = 30

        top_left = (edge_size, edge_size)
        top_right = (width - edge_size, edge_size)
        bottom_right = (width - edge_size, height - edge_size)
        bottom_left = (edge_size, height - edge_size)

        return [top_left, top_right, bottom_right, bottom_left]
